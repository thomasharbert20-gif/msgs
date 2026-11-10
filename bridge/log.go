package gmbridge

import (
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

func (w *rotatingWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.f == nil {
		return len(p), nil
	}
	if w.size+int64(len(p)) > maxLogSize {
		_ = w.f.Close()
		_ = os.Rename(w.path, w.path+".1")
		f, err := os.OpenFile(w.path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
		if err != nil {
			w.f = nil
			return len(p), nil
		}
		w.f, w.size = f, 0
	}
	n, err := w.f.Write(p)
	w.size += int64(n)
	return n, err
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
	return zerolog.New(w).With().Timestamp().Logger().Level(zerolog.DebugLevel)
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
