package actions

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseRef(t *testing.T) {
	good := map[string]Ref{
		"owner/repo@v1":           {Owner: "owner", Repo: "repo", Value: "v1"},
		"owner/repo/sub/dir@main": {Owner: "owner", Repo: "repo", Path: "sub/dir", Value: "main"},
		"o.w-n_er/r.e-p_o@1.2.3":  {Owner: "o.w-n_er", Repo: "r.e-p_o", Value: "1.2.3"},
		"owner/repo@" + sha40:     {Owner: "owner", Repo: "repo", Value: sha40},
		"./.github/actions/build": {Local: "./.github/actions/build"},
		"owner/repo@release/1.x":  {Owner: "owner", Repo: "repo", Value: "release/1.x"},
	}
	for in, want := range good {
		got, err := ParseRef(in)
		require.NoError(t, err, in)
		assert.Equal(t, want, got, in)
	}
	for _, in := range []string{
		"", "owner/repo", "owner@v1", "owner/repo@", "@v1", "owner/../@v1",
		"owner/repo/../../etc@v1", "owner/repo@../x", "docker://alpine:3", "a b/c@v1", "owner//x@v1",
	} {
		_, err := ParseRef(in)
		assert.Error(t, err, in)
	}
}

const sha40 = "0123456789abcdef0123456789abcdef01234567"

func TestParseManifest_composite(t *testing.T) {
	m, err := ParseManifest([]byte(`
name: Hello
description: says hi
inputs:
  who:
    description: person
    required: true
  greeting:
    default: Hello
  retries:
    default: 3
outputs:
  message:
    description: result
    value: ${{ steps.say.outputs.msg }}
runs:
  using: composite
  steps:
    - id: say
      shell: bash
      run: echo "msg=${{ inputs.greeting }}" >> $GITHUB_OUTPUT
      env:
        N: 5
      continue-on-error: true
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0
`))
	require.NoError(t, err)
	assert.Equal(t, KindComposite, m.Runs.Kind())
	assert.True(t, m.Inputs["who"].Required)
	assert.Equal(t, "Hello", m.Inputs["greeting"].Default)
	assert.True(t, m.Inputs["greeting"].HasDefault)
	assert.Equal(t, "3", m.Inputs["retries"].Default, "numeric defaults become strings")
	assert.Equal(t, "${{ steps.say.outputs.msg }}", m.Outputs["message"])
	require.Len(t, m.Runs.Steps, 2)
	assert.Equal(t, "say", m.Runs.Steps[0].ID)
	assert.Equal(t, "5", m.Runs.Steps[0].Env["N"])
	assert.True(t, m.Runs.Steps[0].ContinueOnError)
	assert.Equal(t, "0", m.Runs.Steps[1].With["fetch-depth"])
}

func TestParseManifest_nodeAndDocker(t *testing.T) {
	n, err := ParseManifest([]byte("name: n\nruns:\n  using: node20\n  main: dist/index.js\n  post: dist/post.js\n  post-if: success()\n"))
	require.NoError(t, err)
	assert.Equal(t, KindNode, n.Runs.Kind())
	assert.Equal(t, "dist/post.js", n.Runs.Post)
	assert.Equal(t, "success()", n.Runs.PostIf)

	d, err := ParseManifest([]byte("name: d\nruns:\n  using: docker\n  image: docker://alpine:3\n  args:\n    - ${{ inputs.x }}\n    - 5\n"))
	require.NoError(t, err)
	assert.Equal(t, KindDocker, d.Runs.Kind())
	assert.Equal(t, []string{"${{ inputs.x }}", "5"}, d.Runs.Args)
}

func TestParseManifest_errors(t *testing.T) {
	for name, doc := range map[string]string{
		"unsupported using": "runs:\n  using: java\n",
		"node without main": "runs:\n  using: node20\n",
		"docker no image":   "runs:\n  using: docker\n",
		"invalid yaml":      "runs: [",
		"no runs":           "name: x\n",
	} {
		_, err := ParseManifest([]byte(doc))
		assert.Error(t, err, name)
	}
}

func TestRegistry_builtinMatchesAnyVersion(t *testing.T) {
	reg := NewRegistry("http://localhost", "tok")
	for _, uses := range []string{
		"actions/checkout@v5", "actions/checkout@v99", "actions/checkout@main",
		"actions/setup-node@v6", "actions/upload-artifact@v7", "docker/build-push-action@v9",
	} {
		_, ok := reg.Lookup(uses)
		assert.True(t, ok, uses)
	}
	_, ok := reg.Lookup("actions/github-script@v7")
	assert.False(t, ok, "not a built-in; falls through to remote loading")
	_, ok = reg.Lookup("./local")
	assert.False(t, ok)
}

func gitFixture(t *testing.T, files map[string]string, tag string) string {
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
		{"init", "-q", "-b", "main"},
		{"add", "."},
		{"-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "init"},
		{"tag", tag},
	} {
		cmd := exec.CommandContext(context.Background(), "git", args...)
		cmd.Dir = dir
		out, err := cmd.CombinedOutput()
		require.NoError(t, err, string(out))
	}
	return dir
}

func TestResolver_loadFromGit(t *testing.T) {
	repo := gitFixture(t, map[string]string{
		"action.yml":         "name: root\nruns:\n  using: composite\n  steps: []\n",
		"sub/dir/action.yml": "name: sub\nruns:\n  using: composite\n  steps: []\n",
	}, "v1")
	r := NewResolverWithURL(t.TempDir(), func(owner, name string) string {
		assert.Equal(t, "acme", owner)
		assert.Equal(t, "tool", name)
		return repo
	})

	la, err := r.Load(context.Background(), "acme/tool@v1", t.TempDir())
	require.NoError(t, err)
	assert.Equal(t, "root", la.Manifest.Name)

	la, err = r.Load(context.Background(), "acme/tool/sub/dir@main", t.TempDir())
	require.NoError(t, err)
	assert.Equal(t, "sub", la.Manifest.Name)

	again, err := r.Load(context.Background(), "acme/tool@v1", t.TempDir())
	require.NoError(t, err)
	assert.Equal(t, "root", again.Manifest.Name, "second load reuses the cache")

	_, err = r.Load(context.Background(), "acme/tool@v404", t.TempDir())
	require.Error(t, err)
	assert.Contains(t, err.Error(), "v404")

	_, err = r.Load(context.Background(), "acme/tool/missing@v1", t.TempDir())
	require.Error(t, err)
	assert.Contains(t, err.Error(), "action.yml")
}

func TestResolver_loadLocal(t *testing.T) {
	ws := t.TempDir()
	require.NoError(t, os.MkdirAll(filepath.Join(ws, ".github", "actions", "x"), 0o750))
	require.NoError(t, os.WriteFile(filepath.Join(ws, ".github", "actions", "x", "action.yaml"),
		[]byte("name: local\nruns:\n  using: composite\n  steps: []\n"), 0o600))
	r := NewResolver(t.TempDir())

	la, err := r.Load(context.Background(), "./.github/actions/x", ws)
	require.NoError(t, err)
	assert.Equal(t, "local", la.Manifest.Name)

	_, err = r.Load(context.Background(), "../outside", ws)
	assert.Error(t, err, "must not leave the workspace")
}
