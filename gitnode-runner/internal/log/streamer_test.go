package log

import (
	"encoding/json"
	"strings"
	"testing"

	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

type sink struct{ lines []protocol.LogPayload }

func (k *sink) send(m protocol.OutboundMessage) {
	var p protocol.LogPayload
	_ = json.Unmarshal(m.Data, &p)
	k.lines = append(k.lines, p)
}

func (k *sink) contents() []string {
	out := make([]string, len(k.lines))
	for i, l := range k.lines {
		out[i] = l.Content
	}
	return out
}

func newStreamer(secrets ...string) (*Streamer, *sink) {
	k := &sink{}
	return New("step", secrets, k.send, zap.NewNop()), k
}

func write(s *Streamer, text string) {
	_, _ = s.Write([]byte(text))
	s.Flush()
}

func TestPlainLinesAndPartialWrites(t *testing.T) {
	s, k := newStreamer()
	_, _ = s.Write([]byte("hel"))
	_, _ = s.Write([]byte("lo\nwor"))
	_, _ = s.Write([]byte("ld\n"))
	if got := strings.Join(k.contents(), "|"); got != "hello|world" {
		t.Fatalf("got %q", got)
	}
}

func TestTrailingLineWithoutNewlineIsFlushed(t *testing.T) {
	s, k := newStreamer()
	write(s, "no newline")
	if got := strings.Join(k.contents(), "|"); got != "no newline" {
		t.Fatalf("got %q", got)
	}
}

func TestCRLFIsTrimmed(t *testing.T) {
	s, k := newStreamer()
	write(s, "a\r\nb\r\n")
	if got := strings.Join(k.contents(), "|"); got != "a|b" {
		t.Fatalf("got %q", got)
	}
}

func TestGroupAndAnnotations(t *testing.T) {
	s, k := newStreamer()
	write(s, "::group::Build\nstep one\n::endgroup::\n::error::boom\n::warning file=a.go,line=3,col=7::careful\n::notice title=T::fyi\n")
	want := []string{
		"##[group]Build", "step one", "##[endgroup]", "##[error]boom",
		"##[warning]a.go:3:7: careful", "##[notice]T: fyi",
	}
	if got := k.contents(); strings.Join(got, "|") != strings.Join(want, "|") {
		t.Fatalf("got %v want %v", got, want)
	}
	levels := []string{"info", "info", "info", "error", "warn", "notice"}
	for i, l := range k.lines {
		if l.Level != levels[i] {
			t.Errorf("line %d level %q want %q", i, l.Level, levels[i])
		}
	}
}

func TestAddMaskHidesLaterOutputAndIsReported(t *testing.T) {
	s, k := newStreamer()
	write(s, "::add-mask::s3cret\nthe value is s3cret ok\n")
	if got := strings.Join(k.contents(), "|"); got != "the value is *** ok" {
		t.Fatalf("got %q", got)
	}
	if m := s.NewMasks(); len(m) != 1 || m[0] != "s3cret" {
		t.Fatalf("masks %v", m)
	}
}

func TestSecretsAreMasked(t *testing.T) {
	s, k := newStreamer("hunter2")
	write(s, "password=hunter2\n")
	if got := k.contents()[0]; got != "password=***" {
		t.Fatalf("got %q", got)
	}
}

func TestSetOutputAndSaveState(t *testing.T) {
	s, k := newStreamer()
	write(s, "::set-output name=version::1.2.3\n::save-state name=pid::42\n")
	if s.Outputs()["version"] != "1.2.3" {
		t.Fatalf("outputs %v", s.Outputs())
	}
	if s.State()["pid"] != "42" {
		t.Fatalf("state %v", s.State())
	}
	if len(k.lines) != 1 || !strings.Contains(k.lines[0].Content, "deprecated") {
		t.Fatalf("expected one deprecation warning, got %v", k.contents())
	}
}

func TestEscapes(t *testing.T) {
	s, k := newStreamer()
	write(s, "::error file=a%3Ab%2Cc::100%25 done%0Anext\n")
	if got := k.contents()[0]; got != "##[error]a:b,c: 100% done\nnext" {
		t.Fatalf("got %q", got)
	}
}

func TestStopCommands(t *testing.T) {
	s, k := newStreamer()
	write(s, "::stop-commands::tok\n::error::ignored\n::tok::\n::error::seen\n")
	want := "::error::ignored|##[error]seen"
	if got := strings.Join(k.contents(), "|"); got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

func TestDebugOnlyWhenEnabled(t *testing.T) {
	s, k := newStreamer()
	write(s, "::debug::hidden\n")
	if len(k.lines) != 0 {
		t.Fatalf("debug line leaked: %v", k.contents())
	}
	s.SetDebug(true)
	write(s, "::debug::shown\n")
	if got := k.contents(); len(got) != 1 || got[0] != "##[debug]shown" {
		t.Fatalf("got %v", got)
	}
}

func TestEcho(t *testing.T) {
	s, k := newStreamer()
	write(s, "::echo::on\n::group::G\n")
	if got := strings.Join(k.contents(), "|"); got != "::group::G|##[group]G" {
		t.Fatalf("got %q", got)
	}
}

func TestUnknownCommandIsPlainOutput(t *testing.T) {
	s, k := newStreamer()
	write(s, "::nonsense::x\nsome::text::here\n")
	if got := strings.Join(k.contents(), "|"); got != "::nonsense::x|some::text::here" {
		t.Fatalf("got %q", got)
	}
}

func TestClosePendingGroups(t *testing.T) {
	s, k := newStreamer()
	write(s, "::group::open\nx\n")
	s.ClosePendingGroups()
	if last := k.contents()[len(k.lines)-1]; last != "##[endgroup]" {
		t.Fatalf("last %q", last)
	}
}

func TestDisabledSetEnvIsAnError(t *testing.T) {
	s, k := newStreamer()
	write(s, "::set-env name=A::b\n")
	if got := k.contents()[0]; !strings.Contains(got, "disabled") || k.lines[0].Level != "error" {
		t.Fatalf("got %q level %q", got, k.lines[0].Level)
	}
}
