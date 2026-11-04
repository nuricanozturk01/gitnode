package executor

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/internal/actions"
	"github.com/nuricanozturk/gitnode-runner/internal/workspace"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

// fixtureCmd returns a shell snippet that writes content to path. Workflow `run:` scripts are
// interpolated (like on GitHub), so `${{` is smuggled through as `@@{{` and restored by sed.
func fixtureCmd(path, content string) string {
	c := strings.ReplaceAll(content, "${{", "@@{{")
	return fmt.Sprintf("D='$'\nmkdir -p \"$(dirname %s)\"\ncat > %s <<'FIXTURE_EOF'\n%sFIXTURE_EOF\n"+
		"sed -i.bak \"s/@@{{/${D}{{/g\" %s\nrm -f %s.bak\n", path, path, c, path, path)
}

func runJobWithResolver(t *testing.T, job protocol.JobPayload, r *actions.Resolver) jobOutcome {
	t.Helper()
	rec := newRecorder()
	exec := New(workspace.New(t.TempDir()), actions.NewRegistry("http://localhost", "tok"),
		rec.send, "shell", zap.NewNop())
	exec.resolver = r
	job.ID, job.RunID = "job-1", "run-1"
	exec.Execute(context.Background(), job)
	return rec.outcome(t)
}

func uses(id, ref string, with map[string]string) protocol.StepPayload {
	return protocol.StepPayload{ID: id, Key: id, Uses: ref, With: with}
}

const compositeAction = `name: Greeter
inputs:
  who:
    required: true
  greeting:
    default: Hello
outputs:
  message:
    value: ${{ steps.say.outputs.msg }}
runs:
  using: composite
  steps:
    - id: say
      shell: bash
      run: |
        echo "[${{ inputs.greeting }}, ${{ inputs.who }}] path=${{ github.action_path }}"
        echo "msg=${{ inputs.greeting }} ${{ inputs.who }}" >> "$GITHUB_OUTPUT"
    - shell: bash
      if: inputs.who == 'fail'
      run: exit 1
    - shell: bash
      env:
        WHO: ${{ inputs.who }}
      run: echo "env-who=$WHO action_path_env=$GITHUB_ACTION_PATH"
`

func TestRemote_localCompositeAction(t *testing.T) {
	write := step("write", fixtureCmd(".github/actions/greet/action.yml", compositeAction))
	call := uses("greet", "./.github/actions/greet", map[string]string{"who": "World"})
	after := step("after", `echo "got=${{ steps.greet.outputs.message }}"`)
	cond := step("cond", "echo runs")
	cond.Condition = "steps.greet.outputs.message == 'Hello World'"

	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{write, call, after, cond}},
		actions.NewResolver(t.TempDir()))
	assert.Equal(t, "success", o.conclusion, o.log("greet"))
	assert.Contains(t, o.log("greet"), "[Hello, World] path=")
	assert.Contains(t, o.log("greet"), "env-who=World")
	assert.Contains(t, o.log("greet"), "action_path_env=")
	assert.Equal(t, "Hello World", o.steps["greet"].outputs["message"])
	assert.Contains(t, o.log("after"), "got=Hello World")
	assert.Equal(t, "success", o.steps["cond"].conclusion)
}

func TestRemote_compositeFailureAndMissingInput(t *testing.T) {
	write := step("write", fixtureCmd("act/action.yml", compositeAction))
	fails := uses("fails", "./act", map[string]string{"who": "fail"})
	missing := uses("missing", "./act", nil)
	missing.Condition = "always()"

	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{write, fails, missing}},
		actions.NewResolver(t.TempDir()))
	assert.Equal(t, "failure", o.conclusion)
	assert.Equal(t, "failure", o.steps["fails"].conclusion)
	assert.Equal(t, "failure", o.steps["missing"].conclusion)
	assert.Contains(t, o.log("missing"), `required input "who" is missing`)
}

func TestRemote_compositeFromGit(t *testing.T) {
	repo := gitRepo(t, map[string]string{"action.yml": compositeAction}, "v2")
	r := actions.NewResolverWithURL(t.TempDir(), func(owner, name string) string {
		assert.Equal(t, "acme/greeter", owner+"/"+name)
		return repo
	})
	call := uses("g", "acme/greeter@v2", map[string]string{"who": "Git", "greeting": "Hi"})
	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{call}}, r)
	assert.Equal(t, "success", o.conclusion, o.log("g"))
	assert.Equal(t, "Hi Git", o.steps["g"].outputs["message"])
}

func TestRemote_unreachableActionFailsWithClearMessage(t *testing.T) {
	r := actions.NewResolverWithURL(t.TempDir(), func(owner, name string) string {
		return filepath.Join(t.TempDir(), "does-not-exist")
	})
	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{
		uses("x", "acme/ghost@v1", nil),
	}}, r)
	assert.Equal(t, "failure", o.conclusion)
	assert.Contains(t, o.log("x"), "acme/ghost@v1")
}

func TestRemote_nestedCompositeAndDepthGuard(t *testing.T) {
	inner := `name: inner
inputs:
  v:
    default: x
outputs:
  out:
    value: ${{ steps.s.outputs.r }}
runs:
  using: composite
  steps:
    - id: s
      shell: bash
      run: echo "r=inner-${{ inputs.v }}" >> "$GITHUB_OUTPUT"
`
	outer := `name: outer
outputs:
  final:
    value: ${{ steps.call.outputs.out }}
runs:
  using: composite
  steps:
    - id: call
      uses: ./inner
      with:
        v: nested
`
	loop := "name: loop\nruns:\n  using: composite\n  steps:\n    - uses: ./loop\n"

	write := step("write", fixtureCmd("inner/action.yml", inner)+fixtureCmd("outer/action.yml", outer)+
		fixtureCmd("loop/action.yml", loop))
	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{
		write,
		uses("outer", "./outer", nil),
		uses("loop", "./loop", nil),
	}}, actions.NewResolver(t.TempDir()))
	assert.Equal(t, "failure", o.conclusion)
	assert.Equal(t, "success", o.steps["outer"].conclusion, o.log("outer"))
	assert.Equal(t, "inner-nested", o.steps["outer"].outputs["final"])
	assert.Equal(t, "failure", o.steps["loop"].conclusion)
	assert.Contains(t, o.log("loop"), "nested more than")
}

func TestRemote_nodeActionPreMainPostAndState(t *testing.T) {
	if _, err := exec.LookPath("node"); err != nil {
		t.Skip("node not installed")
	}
	write := step("write", `mkdir -p nodeact
cat > nodeact/action.yml <<'YML'
name: nodeact
inputs:
  who:
    default: nobody
  num-retries:
    default: 3
runs:
  using: node20
  pre: pre.js
  main: main.js
  post: post.js
YML
cat > nodeact/pre.js <<'JS'
require('fs').appendFileSync(process.env.GITHUB_STATE, 'started=yes\n');
console.log('pre-ran');
JS
cat > nodeact/main.js <<'JS'
const fs = require('fs');
console.log('main who=' + process.env.INPUT_WHO + ' retries=' + process.env['INPUT_NUM-RETRIES'] + ' state=' + process.env.STATE_started);
fs.appendFileSync(process.env.GITHUB_OUTPUT, 'answer=' + process.env.INPUT_WHO.toUpperCase() + '\n');
fs.appendFileSync(process.env.GITHUB_ENV, 'FROM_NODE=set-by-node\n');
fs.appendFileSync(process.env.GITHUB_STATE, 'token=abc123\n');
JS
cat > nodeact/post.js <<'JS'
console.log('post who=' + process.env.INPUT_WHO + ' token=' + process.env.STATE_token + ' started=' + process.env.STATE_started);
JS`)
	call := uses("n", "./nodeact", map[string]string{"who": "alice"})
	after := step("after", `echo "env=$FROM_NODE out=${{ steps.n.outputs.answer }}"`)

	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{write, call, after}},
		actions.NewResolver(t.TempDir()))
	assert.Equal(t, "success", o.conclusion, o.log("n"))
	log := o.log("n")
	assert.Contains(t, log, "pre-ran")
	assert.Contains(t, log, "main who=alice retries=3 state=yes")
	assert.Contains(t, log, "post who=alice token=abc123 started=yes", "post runs with saved state")
	assert.Equal(t, "ALICE", o.steps["n"].outputs["answer"])
	assert.Contains(t, o.log("after"), "env=set-by-node out=ALICE")
}

func TestRemote_nodePostRunsEvenWhenJobFails(t *testing.T) {
	if _, err := exec.LookPath("node"); err != nil {
		t.Skip("node not installed")
	}
	write := step("write", `mkdir -p na
printf 'name: na\nruns:\n  using: node20\n  main: m.js\n  post: p.js\n  post-if: failure()\n' > na/action.yml
echo "console.log('main')" > na/m.js
echo "console.log('cleanup-after-failure')" > na/p.js`)
	boom := step("boom", "exit 1")
	o := runJobWithResolver(t, protocol.JobPayload{Steps: []protocol.StepPayload{
		write, uses("na", "./na", nil), boom,
	}}, actions.NewResolver(t.TempDir()))
	assert.Equal(t, "failure", o.conclusion)
	assert.Contains(t, o.log("na"), "cleanup-after-failure")
}

func TestRemote_nodeActionWithoutNodeGivesHint(t *testing.T) {
	t.Setenv("PATH", t.TempDir()) // no node, no docker
	write := step("write", "true")
	_ = write
	ws := t.TempDir()
	require.NoError(t, os.MkdirAll(filepath.Join(ws, "na"), 0o750))
	require.NoError(t, os.WriteFile(filepath.Join(ws, "na", "action.yml"),
		[]byte("name: na\nruns:\n  using: node20\n  main: m.js\n"), 0o600))

	la, err := actions.NewResolver(t.TempDir()).Load(context.Background(), "./na", ws)
	require.NoError(t, err)
	assert.Equal(t, actions.KindNode, la.Manifest.Runs.Kind())
	_, lookErr := exec.LookPath("node")
	assert.Error(t, lookErr, "precondition: node is hidden from PATH")
}

func TestSafeJoin(t *testing.T) {
	d := t.TempDir()
	p, err := safeJoin(d, "dist/index.js")
	require.NoError(t, err)
	assert.Equal(t, filepath.Join(d, "dist", "index.js"), p)
	_, err = safeJoin(d, "../evil.js")
	assert.Error(t, err)
	_, err = safeJoin(d, "a/../../evil.js")
	assert.Error(t, err)
}

func TestInputEnv(t *testing.T) {
	got := inputEnv(map[string]string{"node-version": "20", "with space": "x", "A": "b"})
	assert.Equal(t, map[string]string{"INPUT_NODE-VERSION": "20", "INPUT_WITH_SPACE": "x", "INPUT_A": "b"}, got)
}

func gitRepo(t *testing.T, files map[string]string, tag string) string {
	t.Helper()
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git not installed")
	}
	dir := t.TempDir()
	for name, content := range files {
		full := filepath.Join(dir, name)
		require.NoError(t, os.MkdirAll(filepath.Dir(full), 0o750))
		require.NoError(t, os.WriteFile(full, []byte(content), 0o600))
	}
	for _, args := range [][]string{
		{"init", "-q", "-b", "main"}, {"add", "."},
		{"-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "init"}, {"tag", tag},
	} {
		cmd := exec.CommandContext(context.Background(), "git", args...)
		cmd.Dir = dir
		out, err := cmd.CombinedOutput()
		require.NoError(t, err, string(out))
	}
	return dir
}
