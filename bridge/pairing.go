package gmbridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/events"
)

// Cookies Google Messages for web needs from a signed-in Google session.
var requiredCookies = []string{"SID", "HSID", "SSID", "APISID", "SAPISID", "OSID"}

// MissingCookies returns a comma-separated list of required cookie names that are
// absent from cookiesJSON (a JSON object of name to value). Empty means all present.
func MissingCookies(cookiesJSON string) string {
	var cookies map[string]string
	if err := json.Unmarshal([]byte(cookiesJSON), &cookies); err != nil {
		return strings.Join(requiredCookies, ",")
	}
	var missing []string
	for _, name := range requiredCookies {
		if cookies[name] == "" {
			missing = append(missing, name)
		}
	}
	return strings.Join(missing, ",")
}

// StartPairing begins Google account pairing with the cookies from a signed-in
// messages.google.com session. It returns the emoji the user must tap in Google
// Messages on the phone. Call FinishPairing next; it blocks until they tap it.
func (b *Bridge) StartPairing(cookiesJSON string) (string, error) {
	var cookies map[string]string
	if err := json.Unmarshal([]byte(cookiesJSON), &cookies); err != nil {
		return "", fmt.Errorf("couldn't read the Google sign-in: %w", err)
	}
	if missing := MissingCookies(cookiesJSON); missing != "" {
		return "", fmt.Errorf("Google sign-in is incomplete (missing %s). Sign in again", missing)
	}
	b.CancelPairing()

	ad := libgm.NewAuthData()
	ad.Cookies = cookies
	cli := b.newClient(ad, "libgm-pair")
	cli.SetEventHandler(func(evt any) {
		b.log.Debug().Type("type", evt).Msg("Event during pairing")
	})

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	if err := cli.FetchConfig(ctx); err != nil {
		return "", fmt.Errorf("couldn't reach Google Messages: %w", err)
	}
	if cli.Config.GetDeviceInfo().GetEmail() == "" {
		return "", errors.New("Google didn't accept that sign-in. Sign in again")
	}
	bgCtx, bgCancel := context.WithCancel(context.Background())
	emoji, sess, err := cli.StartGaiaPairing(ctx, bgCtx)
	if err != nil {
		bgCancel()
		cli.Disconnect()
		b.log.Err(err).Msg("Failed to start pairing")
		return "", friendlyPairError(err)
	}
	b.pairMu.Lock()
	b.pairClient = cli
	b.pairSess = sess
	b.pairCancel = bgCancel
	b.pairMu.Unlock()
	b.log.Info().Msg("Pairing started, waiting for emoji confirmation")
	return emoji, nil
}

// FinishPairing waits (up to 5 minutes) for the user to tap the emoji on the phone,
// then saves the pairing and connects.
func (b *Bridge) FinishPairing() error {
	b.pairMu.Lock()
	cli, sess := b.pairClient, b.pairSess
	b.pairMu.Unlock()
	if cli == nil || sess == nil {
		return errors.New("pairing hasn't started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()
	_, err := cli.FinishGaiaPairing(ctx, sess)
	b.pairMu.Lock()
	if b.pairCancel != nil {
		b.pairCancel()
	}
	b.pairClient, b.pairSess, b.pairCancel = nil, nil, nil
	b.pairMu.Unlock()
	cli.Disconnect()
	if err != nil {
		b.log.Err(err).Msg("Failed to finish pairing")
		return friendlyPairError(err)
	}
	b.mu.Lock()
	b.auth = cli.AuthData
	b.mu.Unlock()
	b.saveSession()
	b.log.Info().Msg("Paired with Google Messages")
	b.emit("paired", map[string]any{"account": cli.AuthData.Mobile.GetSourceID()})
	return b.Connect()
}

// CancelPairing abandons a pairing in progress.
func (b *Bridge) CancelPairing() {
	b.pairMu.Lock()
	cli, cancel := b.pairClient, b.pairCancel
	b.pairClient, b.pairSess, b.pairCancel = nil, nil, nil
	b.pairMu.Unlock()
	if cancel != nil {
		cancel()
	}
	if cli != nil {
		cli.Disconnect()
	}
}

func friendlyPairError(err error) error {
	switch {
	case errors.Is(err, libgm.ErrNoDevicesFound):
		return errors.New("no phone found for this Google account. In Google Messages, tap your profile picture, then Device pairing, and make sure it's signed in to the same account")
	case errors.Is(err, libgm.ErrPhoneNotFound):
		return errors.New("your phone isn't ready to pair. Open Google Messages, then try again")
	case errors.Is(err, libgm.ErrPhoneInactive):
		return errors.New("Google Messages hasn't connected to this Google account recently. Open it, check Device pairing is signed in, then try again")
	case errors.Is(err, libgm.ErrPairingInitTimeout):
		return errors.New("the phone didn't respond. Open Google Messages and try again")
	case errors.Is(err, events.ErrCallerNoPermission):
		return errors.New("this Google account can't use Messages for web")
	case errors.Is(err, libgm.ErrIncorrectEmoji):
		return errors.New("the wrong emoji was tapped. Try again")
	case errors.Is(err, libgm.ErrPairingCancelled):
		return errors.New("pairing was cancelled on the phone")
	case errors.Is(err, libgm.ErrPairingTimeout), errors.Is(err, context.DeadlineExceeded):
		return errors.New("pairing timed out. Try again")
	default:
		return fmt.Errorf("pairing failed: %w", err)
	}
}
