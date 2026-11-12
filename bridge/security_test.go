package gmbridge

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"go.mau.fi/mautrix-gmessages/pkg/libgm"
)

type memStore struct {
	data    []byte
	cleared int
}

func (m *memStore) Save(d []byte) error { m.data = append([]byte(nil), d...); return nil }
func (m *memStore) Load() []byte        { return m.data }
func (m *memStore) Clear()              { m.data = nil; m.cleared++ }

func TestLegacyPlaintextSessionIsDeleted(t *testing.T) {
	dir := t.TempDir()
	_ = os.WriteFile(filepath.Join(dir, legacySessionFile), []byte(`{"cookies":{"SID":"x"}}`), 0o600)
	LegacyRemoved = false
	b := NewBridge(dir, nil, &memStore{})
	if _, err := os.Stat(filepath.Join(dir, legacySessionFile)); !os.IsNotExist(err) {
		t.Fatal("legacy session.json still on disk")
	}
	if !LegacyRemoved || b.IsPaired() {
		t.Fatal("legacy removal not reported, or treated as paired")
	}
}

func TestGoogleCookieSessionsAreRefused(t *testing.T) {
	ad := libgm.NewAuthData()
	ad.Cookies = map[string]string{"SID": "secret"}
	raw, _ := json.Marshal(ad)
	st := &memStore{data: raw}
	b := NewBridge(t.TempDir(), nil, st)
	if b.auth != nil || st.cleared == 0 {
		t.Fatal("a stored Google-cookie session was accepted")
	}
	b.auth = ad
	if err := b.saveSessionErr(); err == nil || st.data != nil {
		t.Fatal("bridge saved Google cookies")
	}
}

func TestQRSessionRoundTripsThroughStoreOnly(t *testing.T) {
	dir := t.TempDir()
	st := &memStore{}
	b := NewBridge(dir, nil, st)
	b.auth = libgm.NewAuthData()
	if err := b.saveSessionErr(); err != nil || len(st.data) == 0 {
		t.Fatal("session not handed to the secure store")
	}
	entries, _ := os.ReadDir(dir)
	for _, e := range entries {
		if strings.Contains(e.Name(), "session") {
			t.Fatal("engine wrote a session file itself: " + e.Name())
		}
	}
	b.deleteSession()
	if st.data != nil {
		t.Fatal("unpair didn't clear the store")
	}
}

func TestRedactDropsSensitiveFields(t *testing.T) {
	in := `{"level":"info","message":"x","data":"hello body","evt":{"a":1},"device_id":"d","text":"hi","conv_id":"c1","error":"` + strings.Repeat("e", 400) + `"}`
	out := string(redact([]byte(in)))
	for _, bad := range []string{"hello body", `"evt"`, `"device_id"`, `"text"`} {
		if strings.Contains(out, bad) {
			t.Fatalf("redacted log still contains %s: %s", bad, out)
		}
	}
	if !strings.Contains(out, "conv_id") || len(out) > 300 {
		t.Fatalf("unexpected redaction result: %s", out)
	}
	if string(redact([]byte("not json"))) == "not json" {
		t.Fatal("unparseable line passed through")
	}
}

func TestDestructiveCallsAreDisabled(t *testing.T) {
	b := NewBridge(t.TempDir(), nil, &memStore{})
	if b.DeleteMessage("m") == nil || b.DeleteConversation("c") == nil {
		t.Fatal("delete not blocked")
	}
}
