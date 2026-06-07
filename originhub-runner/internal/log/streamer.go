package log

import (
	"encoding/json"
	"strings"
	"sync/atomic"

	"go.uber.org/zap"

	"github.com/nuricanozturk/originhub-runner/pkg/protocol"
)

// Streamer collects log lines, masks secrets, and forwards them to the server
// via the provided send function.
type Streamer struct {
	stepID  string
	send    func(protocol.OutboundMessage)
	secrets []string
	counter atomic.Int64
	log     *zap.Logger
}

// New creates a Streamer for the given step.
func New(
	stepID string,
	secrets []string,
	send func(protocol.OutboundMessage),
	log *zap.Logger,
) *Streamer {
	return &Streamer{
		stepID:  stepID,
		send:    send,
		secrets: secrets,
		log:     log,
	}
}

// Write implements io.Writer so Streamer can be used as an exec stdout/stderr sink.
func (s *Streamer) Write(p []byte) (int, error) {
	lines := strings.Split(strings.TrimRight(string(p), "\n"), "\n")
	for _, line := range lines {
		s.Emit(line, "info")
	}
	return len(p), nil
}

// Emit masks secrets in content then sends a LOG message.
func (s *Streamer) Emit(content, level string) {
	masked := s.mask(content)
	line := int(s.counter.Add(1))

	payload := protocol.LogPayload{
		StepID:  s.stepID,
		Line:    line,
		Content: masked,
		Level:   level,
	}
	data, err := json.Marshal(payload)
	if err != nil {
		s.log.Warn("failed to marshal log payload", zap.Error(err))
		return
	}
	s.send(protocol.OutboundMessage{
		Type: protocol.TypeLog,
		Data: data,
	})
}

func (s *Streamer) mask(content string) string {
	for _, secret := range s.secrets {
		if secret == "" {
			continue
		}
		content = strings.ReplaceAll(content, secret, "***")
	}
	return content
}
