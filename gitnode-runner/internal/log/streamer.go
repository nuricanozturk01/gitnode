package log

import (
	"encoding/json"
	"strings"
	"sync"

	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

// Streamer turns a step's output into log lines: it buffers partial lines, interprets GitHub
// workflow commands (::group::, ::error::, ::add-mask::, ::set-output::, ...), masks secrets and
// forwards the result to the server through the provided send function.
//
// It is safe for concurrent use, so stdout and stderr of one process may share it.
type Streamer struct {
	stepID string
	send   func(protocol.OutboundMessage)
	log    *zap.Logger

	mu          sync.Mutex
	counter     int
	secrets     []string
	partial     strings.Builder
	outputs     map[string]string
	state       map[string]string
	newMasks    []string
	stopToken   string // non-empty while commands are paused by ::stop-commands::
	echo        bool   // ::echo::on — print the commands themselves
	debug       bool   // ACTIONS_STEP_DEBUG: show ::debug:: lines
	groupsOpen  int
	annotations int
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
		secrets: append([]string(nil), secrets...),
		log:     log,
		outputs: map[string]string{},
		state:   map[string]string{},
	}
}

// SetDebug makes ::debug:: lines visible (GitHub: secret ACTIONS_STEP_DEBUG=true).
func (s *Streamer) SetDebug(on bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.debug = on
}

// Write implements io.Writer so Streamer can be used as an exec stdout/stderr sink. Output is
// split into lines; an unfinished line is kept until its newline arrives or Flush is called.
func (s *Streamer) Write(p []byte) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.partial.Write(p)
	buffered := s.partial.String()
	last := strings.LastIndexByte(buffered, '\n')
	if last < 0 {
		return len(p), nil
	}
	complete, rest := buffered[:last], buffered[last+1:]
	s.partial.Reset()
	s.partial.WriteString(rest)
	for _, line := range strings.Split(complete, "\n") {
		s.handleLine(strings.TrimSuffix(line, "\r"))
	}
	return len(p), nil
}

// Flush emits a trailing line that had no newline. Call it when the process has finished.
func (s *Streamer) Flush() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.partial.Len() == 0 {
		return
	}
	line := strings.TrimSuffix(s.partial.String(), "\r")
	s.partial.Reset()
	s.handleLine(line)
}

// Emit sends one line produced by the runner itself (never interpreted as a command).
func (s *Streamer) Emit(content, level string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.emitLocked(content, level)
}

// Outputs are the step outputs set with the deprecated ::set-output command.
func (s *Streamer) Outputs() map[string]string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return copyMap(s.outputs)
}

// State is what ::save-state stored for the action's post step.
func (s *Streamer) State() map[string]string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return copyMap(s.state)
}

// NewMasks are the values registered with ::add-mask:: so the job can mask them in later steps.
func (s *Streamer) NewMasks() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return append([]string(nil), s.newMasks...)
}

// ClosePendingGroups ends groups the step left open, so later output is not swallowed by them.
func (s *Streamer) ClosePendingGroups() {
	s.mu.Lock()
	defer s.mu.Unlock()
	for ; s.groupsOpen > 0; s.groupsOpen-- {
		s.emitLocked("##[endgroup]", "info")
	}
}

func (s *Streamer) handleLine(line string) {
	if cmd, ok := parseCommand(line); ok {
		if s.stopToken != "" {
			if cmd.name == s.stopToken {
				s.stopToken = ""
				return
			}
			s.emitLocked(line, "info")
			return
		}
		if s.runCommand(cmd, line) {
			return
		}
	}
	s.emitLocked(line, "info")
}

// runCommand executes a workflow command. It returns false when the line is not a command we
// know, in which case it is printed like any other output.
func (s *Streamer) runCommand(cmd command, raw string) bool {
	if s.echo {
		s.emitLocked(raw, "info")
	}
	switch cmd.name {
	case "group":
		s.groupsOpen++
		s.emitLocked("##[group]"+cmd.message, "info")
	case "endgroup":
		if s.groupsOpen > 0 {
			s.groupsOpen--
		}
		s.emitLocked("##[endgroup]", "info")
	case "error":
		s.annotations++
		s.emitLocked("##[error]"+annotationText(cmd), "error")
	case "warning":
		s.annotations++
		s.emitLocked("##[warning]"+annotationText(cmd), "warn")
	case "notice":
		s.annotations++
		s.emitLocked("##[notice]"+annotationText(cmd), "notice")
	case "debug":
		if s.debug {
			s.emitLocked("##[debug]"+cmd.message, "debug")
		}
	case "add-mask":
		s.addMaskLocked(cmd.message)
	case "set-output":
		s.emitLocked("##[warning]The `set-output` command is deprecated; use the GITHUB_OUTPUT environment file", "warn")
		if name := cmd.props["name"]; name != "" {
			s.outputs[name] = cmd.message
		}
	case "save-state":
		if name := cmd.props["name"]; name != "" {
			s.state[name] = cmd.message
		}
	case "stop-commands":
		if cmd.message != "" {
			s.stopToken = cmd.message
		}
	case "echo":
		switch strings.ToLower(strings.TrimSpace(cmd.message)) {
		case "on":
			s.echo = true
		case "off":
			s.echo = false
		}
	case "set-env", "add-path":
		s.emitLocked("##[error]The `"+cmd.name+"` command is disabled. Use the GITHUB_ENV or GITHUB_PATH environment file", "error")
	default:
		return false
	}
	return true
}

func (s *Streamer) addMaskLocked(value string) {
	value = strings.TrimSpace(value)
	if value == "" {
		return
	}
	s.secrets = append(s.secrets, value)
	s.newMasks = append(s.newMasks, value)
}

func (s *Streamer) emitLocked(content, level string) {
	masked := s.maskLocked(content)
	s.counter++

	payload := protocol.LogPayload{
		StepID:  s.stepID,
		Line:    s.counter,
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

func (s *Streamer) maskLocked(content string) string {
	for _, secret := range s.secrets {
		if secret == "" {
			continue
		}
		content = strings.ReplaceAll(content, secret, "***")
	}
	return content
}

func copyMap(in map[string]string) map[string]string {
	out := make(map[string]string, len(in))
	for k, v := range in {
		out[k] = v
	}
	return out
}
