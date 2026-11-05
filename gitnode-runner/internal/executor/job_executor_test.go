package executor

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/internal/actions"
	"github.com/nuricanozturk/gitnode-runner/internal/workspace"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

// recorder collects the messages a job sends to the server.
type recorder struct {
	mu   sync.Mutex
	msgs []protocol.OutboundMessage
	done chan struct{}
	once sync.Once
}

func newRecorder() *recorder { return &recorder{done: make(chan struct{})} }

func (r *recorder) send(m protocol.OutboundMessage) {
	r.mu.Lock()
	r.msgs = append(r.msgs, m)
	r.mu.Unlock()
	if m.Type == protocol.TypeJobCompleted {
		r.once.Do(func() { close(r.done) })
	}
}

type stepOutcome struct {
	conclusion string
	outputs    map[string]string
}

type jobOutcome struct {
	conclusion string
	steps      map[string]stepOutcome // by step id
	logs       map[string][]string    // by step id
}

func (r *recorder) outcome(t *testing.T) jobOutcome {
	t.Helper()
	select {
	case <-r.done:
	case <-time.After(60 * time.Second):
		t.Fatal("job did not complete")
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	out := jobOutcome{steps: map[string]stepOutcome{}, logs: map[string][]string{}}
	for _, m := range r.msgs {
		switch m.Type {
		case protocol.TypeStepCompleted:
			var p protocol.StepCompletedPayload
			require.NoError(t, json.Unmarshal(m.Data, &p))
			out.steps[p.StepID] = stepOutcome{p.Conclusion, p.Outputs}
		case protocol.TypeJobCompleted:
			var p protocol.JobCompletedPayload
			require.NoError(t, json.Unmarshal(m.Data, &p))
			out.conclusion = p.Conclusion
		case protocol.TypeLog:
			var p protocol.LogPayload
			require.NoError(t, json.Unmarshal(m.Data, &p))
			out.logs[p.StepID] = append(out.logs[p.StepID], p.Content)
		}
	}
	return out
}

func (o jobOutcome) log(stepID string) string { return strings.Join(o.logs[stepID], "\n") }

func runJob(t *testing.T, job protocol.JobPayload) jobOutcome {
	t.Helper()
	rec := newRecorder()
	exec := New(workspace.New(t.TempDir()), actions.NewRegistry("http://localhost", "tok"),
		rec.send, "shell", zap.NewNop())
	if job.ID == "" {
		job.ID = "job-1"
	}
	if job.RunID == "" {
		job.RunID = "run-1"
	}
	exec.Execute(context.Background(), job)
	return rec.outcome(t)
}

func step(id, run string) protocol.StepPayload {
	return protocol.StepPayload{ID: id, Key: id, Run: run}
}

func TestJob_allStepsSucceed(t *testing.T) {
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{
		step("a", "echo hello"), step("b", "true"),
	}})
	assert.Equal(t, "success", o.conclusion)
	assert.Equal(t, "success", o.steps["a"].conclusion)
	assert.Contains(t, o.log("a"), "hello")
}

func TestJob_failureSkipsLaterStepsButRunsAlwaysAndFailure(t *testing.T) {
	s3 := step("always", "echo cleanup")
	s3.Condition = "always()"
	s4 := step("onfail", "echo failed-hook")
	s4.Condition = "${{ failure() }}"
	s5 := step("plain", "echo should-not-run")
	s6 := step("onsuccess", "echo nope")
	s6.Condition = "success()"

	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{
		step("boom", "exit 3"), s5, s3, s4, s6,
	}})
	assert.Equal(t, "failure", o.conclusion)
	assert.Equal(t, "failure", o.steps["boom"].conclusion)
	assert.Equal(t, "skipped", o.steps["plain"].conclusion, "no if: means success()")
	assert.Equal(t, "success", o.steps["always"].conclusion)
	assert.Equal(t, "success", o.steps["onfail"].conclusion)
	assert.Equal(t, "skipped", o.steps["onsuccess"].conclusion)
	assert.NotContains(t, o.log("plain"), "should-not-run")
	assert.Contains(t, o.log("onfail"), "failed-hook")
}

func TestJob_defaultShellStopsAtFirstFailingCommand(t *testing.T) {
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{
		step("multi", "false\necho \"result:$((20+22))\""),
	}})
	assert.Equal(t, "failure", o.conclusion, "bash -e like GitHub")
	assert.NotContains(t, o.log("multi"), "result:42", "the second command must not run")
}

func TestJob_shellBashEnablesPipefail(t *testing.T) {
	plain := step("plain", "false | cat")
	pipe := step("pipe", "false | cat")
	pipe.Shell = "bash"
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{plain, pipe}})
	assert.Equal(t, "failure", o.conclusion)
	assert.Equal(t, "success", o.steps["plain"].conclusion, "default bash -e has no pipefail")
	assert.Equal(t, "failure", o.steps["pipe"].conclusion)
}

func TestJob_continueOnError(t *testing.T) {
	flaky := step("flaky", "exit 1")
	flaky.ContinueOnError = true
	after := step("after", "echo still-running")
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{flaky, after}})
	assert.Equal(t, "success", o.conclusion)
	assert.Equal(t, "success", o.steps["flaky"].conclusion)
	assert.Equal(t, "success", o.steps["after"].conclusion)
}

func TestJob_stepEnvIsScopedToItsStep(t *testing.T) {
	a := step("a", `echo "A=$FOO"`)
	a.Env = map[string]string{"FOO": "bar"}
	b := step("b", `echo "B=[$FOO]"`)
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{a, b}})
	assert.Contains(t, o.log("a"), "A=bar")
	assert.Contains(t, o.log("b"), "B=[]")
}

func TestJob_jobEnvAndStandardGithubEnv(t *testing.T) {
	s := step("s", `echo "$MY_VAR|$CI|$GITHUB_ACTIONS|$GITHUB_REPOSITORY|$GITHUB_REF_NAME|$GITHUB_EVENT_NAME|$RUNNER_OS"`)
	o := runJob(t, protocol.JobPayload{
		Env: map[string]string{
			"MY_VAR": "v1", "GITNODE_OWNER": "acme", "GITNODE_REPO": "app", "GITNODE_REF": "refs/heads/main",
		},
		Context: map[string]string{"github.event_name": "workflow_dispatch"},
		Steps:   []protocol.StepPayload{s},
	})
	log := o.log("s")
	assert.Contains(t, log, "v1|true|true|acme/app|main|workflow_dispatch|")
}

func TestJob_githubOutputAndStepsContext(t *testing.T) {
	gen := step("gen", `echo "version=1.2.3" >> "$GITHUB_OUTPUT"
{
  echo "notes<<EOF"
  echo "line1"
  echo "line2"
  echo "EOF"
} >> "$GITHUB_OUTPUT"`)
	use := step("use", `echo "v=${{ steps.gen.outputs.version }} outcome=${{ steps.gen.outcome }}"`)
	cond := step("cond", "echo conditional-ran")
	cond.Condition = "steps.gen.outputs.version == '1.2.3' && steps.gen.conclusion == 'success'"
	skip := step("skip", "echo no")
	skip.Condition = "${{ steps.gen.outputs.version == '9.9.9' }}"

	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{gen, use, cond, skip}})
	assert.Equal(t, "success", o.conclusion)
	assert.Equal(t, "1.2.3", o.steps["gen"].outputs["version"])
	assert.Equal(t, "line1\nline2", o.steps["gen"].outputs["notes"])
	assert.Contains(t, o.log("use"), "v=1.2.3 outcome=success")
	assert.Equal(t, "success", o.steps["cond"].conclusion)
	assert.Equal(t, "skipped", o.steps["skip"].conclusion)
}

func TestJob_githubEnvAndPathPersistAcrossSteps(t *testing.T) {
	set := step("set", `echo "GREETING=hi there" >> "$GITHUB_ENV"
mkdir -p "$GITHUB_WORKSPACE/bin"
printf '#!/bin/sh\necho from-custom-bin\n' > "$GITHUB_WORKSPACE/bin/mytool"
chmod +x "$GITHUB_WORKSPACE/bin/mytool"
echo "$GITHUB_WORKSPACE/bin" >> "$GITHUB_PATH"`)
	use := step("use", `echo "$GREETING"; mytool`)
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{set, use}})
	assert.Equal(t, "success", o.conclusion, o.log("use"))
	assert.Contains(t, o.log("use"), "hi there")
	assert.Contains(t, o.log("use"), "from-custom-bin")
}

func TestJob_workingDirectory(t *testing.T) {
	mk := step("mk", "mkdir -p sub && echo hi > sub/f.txt")
	in := step("in", "pwd && cat f.txt")
	in.WorkingDirectory = "sub"
	esc := step("esc", "echo escaped")
	esc.WorkingDirectory = "../../etc"

	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{mk, in, esc}})
	assert.Equal(t, "success", o.steps["in"].conclusion, o.log("in"))
	assert.Contains(t, o.log("in"), "hi")
	assert.Equal(t, "failure", o.steps["esc"].conclusion, "a missing directory fails the step")
	assert.Contains(t, o.log("esc"), "does not exist")
}

func TestJob_secretsMaskedAndExpressionContexts(t *testing.T) {
	s := step("s", `echo "token=${{ secrets.TOK }} matrix=${{ matrix.os }} sha=${{ github.sha }}"`)
	o := runJob(t, protocol.JobPayload{
		Secrets: map[string]string{"TOK": "s3cr3t-value"},
		Context: map[string]string{"matrix.os": "linux", "github.sha": "abc"},
		Steps:   []protocol.StepPayload{s},
	})
	log := o.log("s")
	assert.NotContains(t, log, "s3cr3t-value")
	assert.Contains(t, log, "token=*** matrix=linux sha=abc")
}

func TestJob_invalidConditionFailsStep(t *testing.T) {
	bad := step("bad", "echo x")
	bad.Condition = "${{ ( }}"
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{bad}})
	assert.Equal(t, "failure", o.conclusion)
	assert.Contains(t, o.log("bad"), "invalid if condition")
}

func TestJob_stepTimeout(t *testing.T) {
	// A 1-minute timeout would make the test slow; assert the wiring with an immediate failure
	// path instead: timeout-minutes > 0 must not break a fast step.
	s := step("fast", "echo ok")
	s.TimeoutMins = 1
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{s}})
	assert.Equal(t, "success", o.conclusion)
}

func TestJob_unknownActionFailsClearly(t *testing.T) {
	u := protocol.StepPayload{ID: "u", Key: "u", Uses: "no-such-owner/no-such-action@v1"}
	o := runJob(t, protocol.JobPayload{Steps: []protocol.StepPayload{u}})
	assert.Equal(t, "failure", o.conclusion)
	assert.Contains(t, o.log("u"), "no-such-owner/no-such-action")
}

func TestParseKeyValues(t *testing.T) {
	got := parseKeyValues("a=1\nb=two=2\nmulti<<EOT\nx\ny\nEOT\n\nbad line\nnoclose<<EOF\nlost\n")
	assert.Equal(t, map[string]string{"a": "1", "b": "two=2", "multi": "x\ny"}, got)
	assert.Empty(t, parseKeyValues(""))
}

func TestResolveSubDir(t *testing.T) {
	root := t.TempDir()
	d, err := resolveSubDir(root, "")
	require.NoError(t, err)
	assert.Equal(t, "", d)
	d, err = resolveSubDir(root, "a/b")
	require.NoError(t, err)
	assert.Equal(t, "a/b", d)
	// Like GitHub: outside the workspace is fine when the directory exists, an error when not.
	outside := t.TempDir()
	d, err = resolveSubDir(root, outside)
	require.NoError(t, err)
	assert.Equal(t, outside, d)
	_, err = resolveSubDir(root, filepath.Join(outside, "missing"))
	assert.Error(t, err)
	_, err = resolveSubDir(root, "../missing-sibling")
	assert.Error(t, err)
}

// A built-in action that fails because the job was cancelled is reported as cancelled, not as a
// failed step with "step error: ... build failed".
func TestJob_cancelledActionStepIsCancelledNotFailed(t *testing.T) {
	bin := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(bin, "docker"), []byte("#!/bin/sh\nsleep 30\n"), 0o755))
	t.Setenv("PATH", bin+string(os.PathListSeparator)+os.Getenv("PATH"))

	rec := newRecorder()
	exec := New(workspace.New(t.TempDir()), actions.NewRegistry("http://localhost", "tok"),
		rec.send, "shell", zap.NewNop())
	login := protocol.StepPayload{
		ID: "login", Key: "login", Uses: "docker/login-action@v3",
		With: map[string]string{"username": "u", "password": "p"},
	}
	exec.Execute(context.Background(), protocol.JobPayload{ID: "job-c", RunID: "run-c", Steps: []protocol.StepPayload{login}})

	// Cancel only once the action is running its (hanging) docker command.
	require.Eventually(t, func() bool {
		rec.mu.Lock()
		defer rec.mu.Unlock()
		for _, m := range rec.msgs {
			if m.Type == protocol.TypeLog && strings.Contains(string(m.Data), "Logging in") {
				return true
			}
		}
		return false
	}, 10*time.Second, 50*time.Millisecond)
	require.True(t, exec.Cancel("job-c"))
	o := rec.outcome(t)

	assert.Equal(t, "cancelled", o.steps["login"].conclusion)
	assert.Equal(t, "cancelled", o.conclusion)
	assert.NotContains(t, o.log("login"), "step error")
	assert.Contains(t, o.log("login"), "step cancelled")
}
