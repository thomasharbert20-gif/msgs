package gmbridge

import (
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"sync"

	"github.com/rs/zerolog"
)

const logFile = "engine.log"
const maxLogSize = 2 << 20

type rotatingWriter struct {
	mu   sync.Mutex
	path string
	f    *os.File
	size int64
}

// redactKeys are log fields that can carry message data, payloads, device lists
// or response bodies. They are dropped before anything reaches the log file.
var redactKeys = []string{"data", "resp_body", "devices", "body", "text", "evt", "panic", "cookies", "token", "url", "number", "phone", "device_id"}

// redact keeps only safe fields: it drops redactKeys and shortens error strings,
// which can embed server responses.
func redact(p []byte) []byte {
	var m map[string]any
	if err := json.Unmarshal(p, &m); err != nil {
		return []byte("{\"level\":\"warn\",\"message\":\"unparseable log line dropped\"}\n")
	}
	for _, k := range redactKeys {
		delete(m, k)
	}
	if e, ok := m["error"].(string); ok && len(e) > 160 {
		m["error"] = e[:160] + "…"
	}
	out, err := json.Marshal(m)
	if err != nil {
		return nil
	}
	return append(out, '\n')
}

func (w *rotatingWriter) Write(in []byte) (int, error) {
	n := len(in)
	p := redact(in)
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.f == nil || len(p) == 0 {
		return n, nil
	}
	if w.size+int64(len(p)) > maxLogSize {
		_ = w.f.Close()
		_ = os.Rename(w.path, w.path+".1")
		f, err := os.OpenFile(w.path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
		if err != nil {
			w.f = nil
			return n, nil
		}
		w.f, w.size = f, 0
	}
	written, err := w.f.Write(p)
	w.size += int64(written)
	if err != nil {
		return n, err
	}
	return n, nil
}

// truncate empties the log files (used when unpairing).
func (w *rotatingWriter) truncate() {
	w.mu.Lock()
	defer w.mu.Unlock()
	_ = os.Remove(w.path + ".1")
	if w.f != nil {
		_ = w.f.Truncate(0)
		_, _ = w.f.Seek(0, io.SeekStart)
	}
	w.size = 0
}

func newLogger(dataDir string) zerolog.Logger {
	path := filepath.Join(dataDir, logFile)
	w := &rotatingWriter{path: path}
	if f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600); err == nil {
		w.f = f
		if st, err := f.Stat(); err == nil {
			w.size = st.Size()
		}
	}
	logWriter = w
	// Info and above only: libgm's debug and trace output includes payloads.
	return zerolog.New(w).With().Timestamp().Logger().Level(zerolog.InfoLevel)
}

var logWriter *rotatingWriter

// WipeLogs deletes the engine log's contents.
func (b *Bridge) WipeLogs() {
	if logWriter != nil {
		logWriter.truncate()
	}
}

// ReadLog returns the last part of the engine log, for the diagnostics screen.
func (b *Bridge) ReadLog() string {
	path := filepath.Join(b.dataDir, logFile)
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	const tail = 96 << 10
	if st, err := f.Stat(); err == nil && st.Size() > tail {
		_, _ = f.Seek(st.Size()-tail, io.SeekStart)
	}
	data, _ := io.ReadAll(f)
	return string(data)
}

// LogLine lets the app write its own lines into the same log.
func (b *Bridge) LogLine(level, msg string) {
	switch level {
	case "error":
		b.log.Error().Str("from", "app").Msg(msg)
	case "warn":
		b.log.Warn().Str("from", "app").Msg(msg)
	default:
		b.log.Info().Str("from", "app").Msg(msg)
	}
}
