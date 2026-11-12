// Package gmbridge wraps the Google Messages web-pairing library (libgm from
// mautrix-gmessages) in a small API that gomobile can expose to the Android app.
//
// Everything crosses the boundary as strings (JSON), []byte, bool and int64,
// because those are the types gomobile can bind.
package gmbridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/util/exhttp"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/events"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

// SessionStore keeps the pairing keys. The Android app implements it with an
// Android Keystore key, so the keys are encrypted at rest and the encryption key
// can't leave the phone. The engine never writes the session to disk itself.
type SessionStore interface {
	Save(data []byte) error
	Load() []byte
	Clear()
}

// Listener receives events from the engine. The Android app implements it.
// kind is a short event name, payload is JSON. Implementations must return quickly.
type Listener interface {
	OnEvent(kind string, payload string)
}

// Bridge is one paired Google Messages session.
type Bridge struct {
	dataDir  string
	listener Listener
	store    SessionStore
	log      zerolog.Logger

	mu     sync.Mutex
	client *libgm.Client
	auth   *libgm.AuthData

	connCtx    context.Context
	connCancel context.CancelFunc

	stateMu         sync.Mutex
	convs           map[string]*gmproto.Conversation
	sims            map[string]*gmproto.SIMCard
	state           string
	phoneResponding bool
	browserActive   bool

	pairMu     sync.Mutex
	pairClient *libgm.Client
	pairDone   chan *gmproto.PairedData
	pairCancel context.CancelFunc

	saveMu sync.Mutex

	events chan [2]string
}

// legacySessionFile is where builds 1-5 kept the session (with Google cookies) in
// plain text. It is deleted on startup and never written again.
const legacySessionFile = "session.json"

// NewBridge creates the engine. dataDir must be a private directory; only the
// engine log lives there. Pairing keys go through store.
func NewBridge(dataDir string, listener Listener, store SessionStore) *Bridge {
	_ = os.MkdirAll(dataDir, 0o700)
	b := &Bridge{
		dataDir:         dataDir,
		listener:        listener,
		store:           store,
		log:             newLogger(dataDir),
		convs:           make(map[string]*gmproto.Conversation),
		sims:            make(map[string]*gmproto.SIMCard),
		state:           "disconnected",
		phoneResponding: true,
		events:          make(chan [2]string, 2048),
	}
	go b.dispatch()
	b.loadSession()
	return b
}

func (b *Bridge) dispatch() {
	for evt := range b.events {
		func() {
			defer func() {
				if r := recover(); r != nil {
					b.log.Error().Str("kind", evt[0]).Msg("Listener panicked")
				}
			}()
			if b.listener != nil {
				b.listener.OnEvent(evt[0], evt[1])
			}
		}()
	}
}

func (b *Bridge) emit(kind string, payload any) {
	var data []byte
	switch p := payload.(type) {
	case string:
		data = []byte(p)
	case []byte:
		data = p
	default:
		var err error
		data, err = json.Marshal(p)
		if err != nil {
			b.log.Warn().Str("kind", kind).Msg("Failed to encode event")
			return
		}
	}
	select {
	case b.events <- [2]string{kind, string(data)}:
	default:
		b.log.Warn().Str("kind", kind).Msg("Event queue full, dropping event")
	}
}

// ---------- session storage ----------

// hasGoogleCreds reports whether a session is a Google-account pairing or holds
// any Google cookies. Neither is ever stored or used by R4 builds.
func hasGoogleCreds(ad *libgm.AuthData) bool {
	return ad.IsGoogleAccount() || len(ad.Cookies) > 0
}

// LegacyRemoved is true when startup found and deleted an old plain-text session.
var LegacyRemoved bool

func (b *Bridge) loadSession() {
	for _, name := range []string{legacySessionFile, legacySessionFile + ".tmp"} {
		if err := os.Remove(filepath.Join(b.dataDir, name)); err == nil {
			LegacyRemoved = true
		}
	}
	if b.store == nil {
		return
	}
	data := b.store.Load()
	if len(data) == 0 {
		return
	}
	var ad libgm.AuthData
	if err := json.Unmarshal(data, &ad); err != nil {
		b.log.Warn().Msg("Saved session is unreadable; discarding it")
		b.store.Clear()
		return
	}
	if hasGoogleCreds(&ad) {
		// Google-account sessions carry broad Google cookies. Refuse them.
		b.log.Warn().Msg("Discarding a Google-account session; pair again by QR code")
		b.store.Clear()
		return
	}
	b.auth = &ad
	b.log.Info().Msg("Loaded saved session")
}

func (b *Bridge) saveSession() {
	if err := b.saveSessionErr(); err != nil {
		b.log.Warn().Msg("Couldn't save session")
	}
}

func (b *Bridge) saveSessionErr() error {
	b.saveMu.Lock()
	defer b.saveMu.Unlock()
	b.mu.Lock()
	ad := b.auth
	b.mu.Unlock()
	if ad == nil {
		return nil
	}
	if b.store == nil {
		return errors.New("no secure store")
	}
	if hasGoogleCreds(ad) {
		return errors.New("refusing to store Google cookies")
	}
	data, err := json.Marshal(ad)
	if err != nil {
		return err
	}
	return b.store.Save(data)
}

func (b *Bridge) deleteSession() {
	b.saveMu.Lock()
	defer b.saveMu.Unlock()
	if b.store != nil {
		b.store.Clear()
	}
}

// IsPaired reports whether a usable pairing is saved.
func (b *Bridge) IsPaired() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.auth != nil && b.auth.Browser != nil && b.auth.TachyonAuthToken != nil
}

// ---------- connection ----------

func (b *Bridge) newClient(ad *libgm.AuthData, logName string) *libgm.Client {
	cli := libgm.NewClient(ad, nil, b.log.With().Str("component", logName).Logger(), exhttp.SensibleClientSettings)
	cli.SetPingInterval(20 * time.Minute)
	return cli
}

// Connect opens the live connection using the saved pairing. It returns once the
// connection has started; progress arrives as "status" events.
func (b *Bridge) Connect() error {
	b.mu.Lock()
	if b.auth == nil || b.auth.Browser == nil {
		b.mu.Unlock()
		return errors.New("not paired")
	}
	if b.client != nil && b.client.IsConnected() {
		b.mu.Unlock()
		return nil
	}
	if b.client != nil {
		b.client.SetEventHandler(nil)
		b.client.Disconnect()
	}
	if b.connCancel != nil {
		b.connCancel()
	}
	cli := b.newClient(b.auth, "libgm")
	cli.SetEventHandler(b.handleEvent)
	b.client = cli
	b.connCtx, b.connCancel = context.WithCancel(context.Background())
	ctx := b.connCtx
	b.mu.Unlock()

	b.setState("connecting", "")
	fetchCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	err := cli.FetchConfig(fetchCtx)
	cancel()
	if err != nil {
		b.log.Warn().Msg("Failed to fetch config")
	}
	if err = cli.Connect(ctx); err != nil {
		if isCredentialError(err) {
			b.markUnpaired("credentials")
			return errors.New("the pairing is no longer valid; pair again")
		}
		b.setState("error", err.Error())
		return fmt.Errorf("couldn't connect: %w", err)
	}
	b.setState("connected", "")
	go b.saveSession()
	return nil
}

// Disconnect closes the live connection but keeps the pairing.
func (b *Bridge) Disconnect() {
	b.mu.Lock()
	cli := b.client
	b.client = nil
	if b.connCancel != nil {
		b.connCancel()
		b.connCancel = nil
	}
	b.mu.Unlock()
	if cli != nil {
		cli.SetEventHandler(nil)
		cli.Disconnect()
	}
	b.saveSession()
	b.setState("disconnected", "")
}

// IsConnected reports whether the live connection is up.
func (b *Bridge) IsConnected() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.client != nil && b.client.IsConnected()
}

// SetActive makes this app the active Messages-for-web session again, after
// another browser took over.
func (b *Bridge) SetActive() error {
	cli, err := b.cli()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	return cli.SetActiveSession(ctx)
}

// Unpair removes the pairing on Google's side and deletes it here.
func (b *Bridge) Unpair() error {
	b.mu.Lock()
	cli := b.client
	auth := b.auth
	b.mu.Unlock()
	err := errors.New("no saved pairing")
	if cli == nil && auth != nil {
		// Not connected right now: use a short-lived client just to send the revoke.
		cli = b.newClient(auth, "unpair")
		defer cli.Disconnect()
	}
	if cli != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		err = cli.Unpair(ctx)
		cancel()
	}
	b.Disconnect()
	b.mu.Lock()
	b.auth = nil
	b.mu.Unlock()
	b.deleteSession()
	b.stateMu.Lock()
	b.convs = make(map[string]*gmproto.Conversation)
	b.sims = make(map[string]*gmproto.SIMCard)
	b.stateMu.Unlock()
	b.WipeLogs()
	b.setState("unpaired", "")
	if err != nil {
		return errors.New("removed from this phone, but Google didn't confirm the revoke. Check Google Messages > Device pairing")
	}
	return nil
}

// Status returns the connection status as JSON.
func (b *Bridge) Status() string {
	paired := b.IsPaired()
	b.stateMu.Lock()
	defer b.stateMu.Unlock()
	data, _ := json.Marshal(map[string]any{
		"state":           b.state,
		"paired":          paired,
		"phoneResponding": b.phoneResponding,
		"active":          b.browserActive,
	})
	return string(data)
}

func (b *Bridge) setState(state, detail string) {
	b.stateMu.Lock()
	b.state = state
	payload := map[string]any{
		"state":           state,
		"detail":          detail,
		"phoneResponding": b.phoneResponding,
		"active":          b.browserActive,
	}
	b.stateMu.Unlock()
	b.emit("status", payload)
}

func (b *Bridge) markUnpaired(reason string) {
	b.log.Warn().Str("reason", reason).Msg("Session is no longer paired")
	b.mu.Lock()
	cli := b.client
	b.client = nil
	b.auth = nil
	if b.connCancel != nil {
		b.connCancel()
		b.connCancel = nil
	}
	b.mu.Unlock()
	if cli != nil {
		cli.SetEventHandler(nil)
		go cli.Disconnect()
	}
	b.deleteSession()
	b.setState("unpaired", reason)
}

func (b *Bridge) cli() (*libgm.Client, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.client == nil {
		return nil, errors.New("not connected")
	}
	return b.client, nil
}

func isCredentialError(err error) bool {
	if errors.Is(err, events.ErrInvalidCredentials) || errors.Is(err, events.ErrRequestedEntityNotFound) {
		return true
	}
	var httpErr events.HTTPError
	if errors.As(err, &httpErr) && httpErr.Resp != nil {
		switch httpErr.Resp.StatusCode {
		case 401, 403:
			return true
		}
	}
	return false
}

// ---------- events from libgm ----------

// handleEvent is called synchronously by libgm and must not block.
func (b *Bridge) handleEvent(raw any) {
	switch evt := raw.(type) {
	case *gmproto.Conversation:
		b.storeConv(evt)
		b.emit("conversation", b.convToMap(evt))
	case *libgm.WrappedMessage:
		b.emit("message", b.msgToMap(evt.Message, evt.IsOld))
	case *gmproto.TypingData:
		b.emit("typing", map[string]any{
			"conversationId": evt.GetConversationID(),
			"number":         evt.GetUser().GetNumber(),
			"typing":         evt.GetType() == gmproto.TypingTypes_STARTED_TYPING,
		})
	case *gmproto.Settings:
		b.storeSIMs(evt.GetSIMCards())
		b.emit("settings", b.settingsToMap(evt))
	case *gmproto.UserAlertEvent:
		b.handleAlert(evt)
	case *events.ListenFatalError:
		if isCredentialError(evt.Error) {
			go b.markUnpaired("credentials")
		} else {
			b.setState("error", evt.Error.Error())
		}
	case *events.ListenTemporaryError:
		b.setState("reconnecting", evt.Error.Error())
	case *events.ListenRecovered:
		b.setState("connected", "")
	case *events.PhoneNotResponding:
		b.stateMu.Lock()
		b.phoneResponding = false
		b.stateMu.Unlock()
		b.setState("phone_not_responding", "")
	case *events.PhoneRespondingAgain:
		b.stateMu.Lock()
		b.phoneResponding = true
		b.stateMu.Unlock()
		b.setState("connected", "")
	case *events.PingFailed:
		if errors.Is(evt.Error, events.ErrRequestedEntityNotFound) {
			go b.markUnpaired("unpaired_from_phone")
		}
	case *gmproto.RevokePairData:
		go b.markUnpaired("revoked")
	case *events.GaiaLoggedOut:
		go b.markUnpaired("google_logged_out")
	case *events.AuthTokenRefreshed:
		go b.saveSession()
	case *events.HackySetActiveMayFail:
		go func() {
			time.Sleep(7 * time.Second)
			b.stateMu.Lock()
			active := b.browserActive
			b.stateMu.Unlock()
			if !active {
				_ = b.SetActive()
			}
		}()
	case *events.NoDataReceived:
		b.emit("resync", map[string]any{})
	case *events.PairSuccessful:
		b.log.Debug().Msg("Pair successful event on main client")
	default:
		b.log.Trace().Type("type", raw).Msg("Unhandled event")
	}
}

func (b *Bridge) handleAlert(evt *gmproto.UserAlertEvent) {
	switch evt.GetAlertType() {
	case gmproto.AlertType_BROWSER_ACTIVE:
		b.stateMu.Lock()
		was := b.browserActive
		b.browserActive = true
		b.stateMu.Unlock()
		b.setState("connected", "")
		if !was {
			b.emit("resync", map[string]any{})
		}
	case gmproto.AlertType_BROWSER_INACTIVE, gmproto.AlertType_BROWSER_INACTIVE_FROM_TIMEOUT, gmproto.AlertType_BROWSER_INACTIVE_FROM_INACTIVITY:
		b.stateMu.Lock()
		b.browserActive = false
		b.stateMu.Unlock()
		b.setState("inactive", evt.GetAlertType().String())
	case gmproto.AlertType_MOBILE_DATABASE_SYNC_COMPLETE:
		b.emit("resync", map[string]any{})
	case gmproto.AlertType_MOBILE_BATTERY_LOW:
		b.emit("alert", map[string]any{"type": "battery_low"})
	}
}

func (b *Bridge) storeConv(conv *gmproto.Conversation) {
	if conv == nil || conv.GetConversationID() == "" {
		return
	}
	b.stateMu.Lock()
	b.convs[conv.GetConversationID()] = conv
	b.stateMu.Unlock()
}

func (b *Bridge) getConv(ctx context.Context, convID string) (*gmproto.Conversation, error) {
	b.stateMu.Lock()
	conv := b.convs[convID]
	b.stateMu.Unlock()
	if conv != nil {
		return conv, nil
	}
	cli, err := b.cli()
	if err != nil {
		return nil, err
	}
	conv, err = cli.GetConversation(ctx, convID)
	if err != nil {
		return nil, fmt.Errorf("couldn't load the conversation: %w", err)
	}
	b.storeConv(conv)
	return conv, nil
}

func (b *Bridge) storeSIMs(sims []*gmproto.SIMCard) {
	if sims == nil {
		return
	}
	b.stateMu.Lock()
	defer b.stateMu.Unlock()
	m := make(map[string]*gmproto.SIMCard, len(sims))
	for _, sim := range sims {
		m[sim.GetSIMParticipant().GetID()] = sim
	}
	b.sims = m
}

func (b *Bridge) simPayloadFor(conv *gmproto.Conversation) *gmproto.SIMPayload {
	b.stateMu.Lock()
	defer b.stateMu.Unlock()
	if sim, ok := b.sims[conv.GetDefaultOutgoingID()]; ok {
		return sim.GetSIMData().GetSIMPayload()
	}
	if conv.GetSimCard() != nil {
		return conv.GetSimCard().GetSIMData().GetSIMPayload()
	}
	for _, sim := range b.sims {
		return sim.GetSIMData().GetSIMPayload()
	}
	return nil
}
