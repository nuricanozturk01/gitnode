package executor

import (
	"encoding/json"
	"os"
	"testing"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

func TestFlattenEvent(t *testing.T) {
	got := flattenEvent(json.RawMessage(
		`{"number":7,"draft":false,"pull_request":{"title":"Fix it","head":{"ref":"feat"}},"commits":[{"id":"abc"}],"x":null}`))
	want := map[string]string{
		"github.event.number":                "7",
		"github.event.draft":                 "false",
		"github.event.pull_request.title":    "Fix it",
		"github.event.pull_request.head.ref": "feat",
		"github.event.commits.0.id":          "abc",
		"github.event.x":                     "",
	}
	for k, v := range want {
		if got[k] != v {
			t.Errorf("%s = %q, want %q", k, got[k], v)
		}
	}
	if got["github.event"] == "" {
		t.Error("whole event JSON is missing")
	}
}

func TestFlattenEventIgnoresInvalidOrEmpty(t *testing.T) {
	if len(flattenEvent(nil)) != 0 || len(flattenEvent(json.RawMessage("{oops"))) != 0 {
		t.Fatal("expected no keys")
	}
}

func TestWriteEventFileDefaultsToEmptyObject(t *testing.T) {
	dir := t.TempDir()
	path, err := writeEventFile(dir, nil)
	if err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(path)
	if string(data) != "{}" {
		t.Fatalf("got %q", data)
	}
}

func TestEvalJobOutputs(t *testing.T) {
	job := protocol.JobPayload{
		Outputs: map[string]string{
			"version": "${{ steps.ver.outputs.v }}",
			"fixed":   "1.0",
			"broken":  "${{ nonsense( }}",
		},
	}
	results := map[string]*stepResult{
		"ver": {outputs: map[string]string{"v": "2.3.4"}, outcome: "success", conclusion: "success"},
	}
	rt := &runtimeFiles{dir: t.TempDir()}
	got := evalJobOutputs(job, jobctx.New(nil, nil), "success", results, t.TempDir(), rt)
	if got["version"] != "2.3.4" || got["fixed"] != "1.0" || got["broken"] != "" {
		t.Fatalf("got %v", got)
	}
	if evalJobOutputs(protocol.JobPayload{}, jobctx.New(nil, nil), "success", nil, "", rt) != nil {
		t.Fatal("no outputs expected")
	}
}
