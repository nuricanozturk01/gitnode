package actions

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

func gitIn(t *testing.T, dir string, args ...string) string {
	t.Helper()
	cmd := exec.CommandContext(context.Background(), "git", args...)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	require.NoError(t, err, string(out))
	return strings.TrimSpace(string(out))
}

// checkoutFixture: main has a.txt=main2 (two commits), branch feature has b.txt, tag v1 is the
// first commit. It returns the repo path and the SHAs of the first and second commit.
func checkoutFixture(t *testing.T) (repo, first, second string) {
	t.Helper()
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git not installed")
	}
	repo = t.TempDir()
	gitIn(t, repo, "init", "-q", "-b", "main")
	commit := func(file, content, msg string) {
		require.NoError(t, os.WriteFile(filepath.Join(repo, file), []byte(content), 0o600))
		gitIn(t, repo, "add", ".")
		gitIn(t, repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", msg)
	}
	commit("a.txt", "main1", "one")
	first = gitIn(t, repo, "rev-parse", "HEAD")
	gitIn(t, repo, "tag", "v1")
	commit("a.txt", "main2", "two")
	second = gitIn(t, repo, "rev-parse", "HEAD")
	gitIn(t, repo, "checkout", "-q", "-b", "feature")
	commit("b.txt", "feature", "feat")
	gitIn(t, repo, "checkout", "-q", "main")
	return repo, first, second
}

func nopStreamer() *runnerlog.Streamer {
	return runnerlog.New("s", nil, func(protocol.OutboundMessage) {}, zap.NewNop())
}

func readFile(t *testing.T, dir, name string) string {
	t.Helper()
	b, err := os.ReadFile(filepath.Join(dir, name)) //nolint:gosec
	if err != nil {
		return "<missing>"
	}
	return string(b)
}

func TestCloneAndCheckout(t *testing.T) {
	repo, first, second := checkoutFixture(t)
	ctx := context.Background()

	cases := []struct {
		name, target string
		depth        int
		a, b         string // expected a.txt and b.txt
	}{
		{"default branch", "", 1, "main2", "<missing>"},
		{"branch name", "feature", 1, "main2", "feature"},
		{"full branch ref", "refs/heads/feature", 1, "main2", "feature"},
		{"main by name", "main", 1, "main2", "<missing>"},
		{"tag name", "v1", 1, "main1", "<missing>"},
		{"full tag ref", "refs/tags/v1", 1, "main1", "<missing>"},
		{"older commit sha", first, 0, "main1", "<missing>"},
		{"tip commit sha", second, 0, "main2", "<missing>"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			dest := filepath.Join(t.TempDir(), "ws")
			err := cloneAndCheckout(ctx, repo, nil, dest, tc.target, tc.depth, nopStreamer())
			require.NoError(t, err)
			assert.Equal(t, tc.a, readFile(t, dest, "a.txt"))
			assert.Equal(t, tc.b, readFile(t, dest, "b.txt"))
		})
	}

	t.Run("unknown ref fails clearly", func(t *testing.T) {
		dest := filepath.Join(t.TempDir(), "ws")
		err := cloneAndCheckout(ctx, repo, nil, dest, "no-such-branch", 1, nopStreamer())
		require.Error(t, err)
		assert.Contains(t, err.Error(), "no-such-branch")
	})

	t.Run("unknown sha fails clearly", func(t *testing.T) {
		dest := filepath.Join(t.TempDir(), "ws")
		err := cloneAndCheckout(ctx, repo, nil, dest, strings.Repeat("a", 40), 0, nopStreamer())
		require.Error(t, err)
	})

	t.Run("shallow clone has a single commit", func(t *testing.T) {
		dest := filepath.Join(t.TempDir(), "ws")
		require.NoError(t, cloneAndCheckout(ctx, repo, nil, dest, "", 1, nopStreamer()))
		assert.Equal(t, "1", gitIn(t, dest, "rev-list", "--count", "HEAD"))
	})

	t.Run("depth 0 fetches the full history", func(t *testing.T) {
		dest := filepath.Join(t.TempDir(), "ws")
		require.NoError(t, cloneAndCheckout(ctx, repo, nil, dest, "", 0, nopStreamer()))
		assert.Equal(t, "2", gitIn(t, dest, "rev-list", "--count", "HEAD"))
	})
}

func TestCheckoutTarget(t *testing.T) {
	env := map[string]string{"GITNODE_SHA": "abc", "GITNODE_REF": "refs/heads/dev"}
	assert.Equal(t, "x", checkoutTarget(map[string]string{"ref": " x "}, env), "input wins")
	assert.Equal(t, "abc", checkoutTarget(nil, env), "then the run's commit")
	assert.Equal(t, "refs/heads/dev", checkoutTarget(nil, map[string]string{"GITNODE_REF": "refs/heads/dev"}),
		"then the triggering ref, so dispatch on a branch checks that branch out")
	assert.Equal(t, "", checkoutTarget(nil, nil))
}

func TestCheckoutDepth(t *testing.T) {
	assert.Equal(t, 1, checkoutDepth(nil))
	assert.Equal(t, 0, checkoutDepth(map[string]string{"fetch-depth": "0"}))
	assert.Equal(t, 5, checkoutDepth(map[string]string{"fetch-depth": "5"}))
	assert.Equal(t, 0, checkoutDepth(map[string]string{"depth": "0"}))
	assert.Equal(t, 0, checkoutDepth(map[string]string{"depth": "full"}))
	assert.Equal(t, 1, checkoutDepth(map[string]string{"fetch-depth": "nonsense"}))
	assert.Equal(t, 1, checkoutDepth(map[string]string{"fetch-depth": "-3"}))
}

func TestCheckoutDest(t *testing.T) {
	root := t.TempDir()
	d, err := checkoutDest(root, "")
	require.NoError(t, err)
	assert.Equal(t, root, d)
	d, err = checkoutDest(root, "sub/dir")
	require.NoError(t, err)
	assert.Equal(t, filepath.Join(root, "sub", "dir"), d)
	_, err = checkoutDest(root, "../escape")
	assert.Error(t, err)
}
