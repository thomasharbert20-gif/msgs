package gmbridge

import (
	"context"
	"errors"
	"strings"
	"time"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

// QR pairing, the same flow messages.google.com uses when you choose "Pair with
// QR code". No Google account sign-in and no Google cookies are involved: the
// session that results holds only keys scoped to this one Messages-for-web
// pairing, which can be revoked from Google Messages > Device pairing.

// StartQRPairing begins a pairing attempt and returns the QR code contents. The
// code expires after about 30 seconds; call RefreshQR to get a new one.
func (b *Bridge) StartQRPairing() (string, error) {
	b.CancelPairing()
	cli := b.newClient(libgm.NewAuthData(), "pair")
	cli.SetEventHandler(func(evt any) {
		b.log.Debug().Type("type", evt).Msg("Event during pairing")
	})
	done := make(chan *gmproto.PairedData, 1)
	cb := func(data *gmproto.PairedData) {
		select {
		case done <- data:
		default:
		}
	}
	cli.PairCallback.Store(&cb)

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	err := cli.FetchConfig(ctx)
	cancel()
	if err != nil {
		b.log.Warn().Msg("Couldn't fetch config before pairing")
		return "", errors.New("couldn't reach Google Messages. Check your connection and try again")
	}
	bgCtx, bgCancel := context.WithCancel(context.Background())
	qr, err := cli.StartLogin(bgCtx)
	if err != nil {
		bgCancel()
		cli.Disconnect()
		b.log.Warn().Msg("Couldn't start QR pairing")
		return "", friendlyPairError(err)
	}
	b.pairMu.Lock()
	b.pairClient, b.pairDone, b.pairCancel = cli, done, bgCancel
	b.pairMu.Unlock()
	b.log.Info().Msg("QR pairing started")
	return qr, nil
}

// RefreshQR returns a fresh QR code for the pairing attempt in progress.
func (b *Bridge) RefreshQR() (string, error) {
	b.pairMu.Lock()
	cli := b.pairClient
	b.pairMu.Unlock()
	if cli == nil {
		return "", errors.New("pairing hasn't started")
	}
	qr, err := cli.RefreshPhoneRelay()
	if err != nil {
		b.log.Warn().Msg("Couldn't refresh QR code")
		return "", errors.New("couldn't get a new QR code. Start pairing again")
	}
	return qr, nil
}

// WaitForPairing waits up to timeoutMs for the phone to scan the code. It returns
// false if the time ran out (show a fresh code and wait again), true once the
// pairing is saved securely and the connection has started.
func (b *Bridge) WaitForPairing(timeoutMs int64) (bool, error) {
	b.pairMu.Lock()
	cli, done := b.pairClient, b.pairDone
	b.pairMu.Unlock()
	if cli == nil || done == nil {
		return false, errors.New("pairing hasn't started")
	}
	var data *gmproto.PairedData
	select {
	case data = <-done:
	case <-time.After(time.Duration(timeoutMs) * time.Millisecond):
		return false, nil
	}
	b.CancelPairing()
	if data == nil || cli.AuthData.Browser == nil || cli.AuthData.TachyonAuthToken == nil {
		return false, errors.New("the phone didn't finish pairing. Try again")
	}
	// libgm: reconnecting too quickly after pairing can make the phone drop the
	// new session, so give it a moment to save the pairing.
	time.Sleep(2 * time.Second)
	b.mu.Lock()
	b.auth = cli.AuthData
	b.mu.Unlock()
	if err := b.saveSessionErr(); err != nil {
		// Never keep running on a pairing we couldn't store securely.
		b.mu.Lock()
		b.auth = nil
		b.mu.Unlock()
		b.log.Error().Msg("Couldn't store the pairing securely")
		return false, errors.New("couldn't store the pairing securely on this phone. Nothing was saved")
	}
	b.log.Info().Msg("Paired with Google Messages by QR code")
	b.emit("paired", map[string]any{})
	// The pairing is saved either way. A failed first connect shows up as a status
	// event and the app retries; returning an error here would hide the success.
	if err := b.Connect(); err != nil {
		b.log.Warn().Msg("First connect after pairing failed")
	}
	return true, nil
}

// CancelPairing abandons a pairing in progress.
func (b *Bridge) CancelPairing() {
	b.pairMu.Lock()
	cli, cancel := b.pairClient, b.pairCancel
	b.pairClient, b.pairDone, b.pairCancel = nil, nil, nil
	b.pairMu.Unlock()
	if cancel != nil {
		cancel()
	}
	if cli != nil {
		cli.SetEventHandler(nil)
		cli.Disconnect()
	}
}

func friendlyPairError(err error) error {
	msg := err.Error()
	switch {
	case strings.Contains(msg, "429"):
		return errors.New("Google is rate-limiting pairing attempts. Wait a few minutes and try again")
	case strings.Contains(msg, "context deadline exceeded"), strings.Contains(msg, "timeout"):
		return errors.New("Google Messages didn't respond in time. Try again")
	default:
		return errors.New("couldn't start pairing. Try again")
	}
}
