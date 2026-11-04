package actions

import (
	"context"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/plumbing"
	gogithttp "github.com/go-git/go-git/v5/plumbing/transport/http"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

// CheckoutAction implements actions/checkout@v1.
// It clones the repository using the runner's job token injected into the HTTP URL.
type CheckoutAction struct {
	serverURL   string
	runnerToken string
}

func NewCheckoutAction(serverURL, runnerToken string) *CheckoutAction {
	return &CheckoutAction{serverURL: serverURL, runnerToken: runnerToken}
}

func (a *CheckoutAction) Name() string       { return "actions/checkout" }
func (a *CheckoutAction) Versions() []string { return []string{"v1", "v2", "v3", "v4"} }

func (a *CheckoutAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	env := jctx.Env()
	owner := env["GITNODE_OWNER"]
	repo := env["GITNODE_REPO"]

	if owner == "" || repo == "" {
		return nil, fmt.Errorf("checkout: GITNODE_OWNER or GITNODE_REPO not set in env")
	}

	target := checkoutTarget(inputs, env)
	depth := checkoutDepth(inputs)

	repoURL, err := buildGitURL(a.serverURL, owner, repo)
	if err != nil {
		return nil, fmt.Errorf("checkout: build URL: %w", err)
	}
	auth := &gogithttp.BasicAuth{Username: "x-token", Password: a.runnerToken}

	dest, err := checkoutDest(workDir, inputs["path"])
	if err != nil {
		return nil, err
	}

	streamer.Emit(fmt.Sprintf("Cloning %s/%s ref=%s (depth=%d)", owner, repo, target, depth), "info")
	if err := cloneAndCheckout(ctx, repoURL, auth, dest, target, depth, streamer); err != nil {
		return nil, err
	}
	streamer.Emit("Checkout complete", "info")
	return map[string]string{}, nil
}

// checkoutTarget picks what to check out: the `ref` input, else the commit of the run, else
// the ref the run was triggered on. Empty means the repository's default branch.
func checkoutTarget(inputs, env map[string]string) string {
	for _, v := range []string{inputs["ref"], env["GITNODE_SHA"], env["GITNODE_REF"]} {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

// checkoutDepth reads fetch-depth (GitHub) or depth (legacy). 0 means full history; the default
// is a shallow clone of one commit, like GitHub.
func checkoutDepth(inputs map[string]string) int {
	raw := inputs["fetch-depth"]
	if raw == "" {
		raw = inputs["depth"]
	}
	if raw == "full" {
		return 0
	}
	if n, err := strconv.Atoi(strings.TrimSpace(raw)); err == nil && n >= 0 {
		return n
	}
	return 1
}

// checkoutDest resolves the `path` input below the workspace and rejects escapes.
func checkoutDest(workDir, sub string) (string, error) {
	if strings.TrimSpace(sub) == "" {
		return workDir, nil
	}
	dest := filepath.Join(workDir, sub)
	rel, err := filepath.Rel(workDir, dest)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("checkout: path %q is outside the workspace", sub)
	}
	return dest, nil
}

var shaPattern = regexp.MustCompile(`^[0-9a-fA-F]{40}$`)

// cloneAndCheckout clones url into dest and leaves the worktree at target, which may be a
// commit SHA, a full ref (refs/heads/x, refs/tags/x), a branch or tag name, or empty.
func cloneAndCheckout(
	ctx context.Context,
	url string,
	auth *gogithttp.BasicAuth,
	dest, target string,
	depth int,
	streamer *runnerlog.Streamer,
) error {
	if err := os.MkdirAll(dest, 0o750); err != nil {
		return fmt.Errorf("checkout: create %s: %w", dest, err)
	}

	switch {
	case target == "":
		_, err := git.PlainCloneContext(ctx, dest, false, &git.CloneOptions{URL: url, Auth: auth, Depth: depth})
		return wrapCheckout(err, "clone")
	case shaPattern.MatchString(target):
		// A commit may not be the tip of any branch, so the history is needed to reach it.
		repo, err := git.PlainCloneContext(ctx, dest, false, &git.CloneOptions{URL: url, Auth: auth})
		if err != nil {
			return wrapCheckout(err, "clone")
		}
		wt, err := repo.Worktree()
		if err != nil {
			return wrapCheckout(err, "worktree")
		}
		return wrapCheckout(wt.Checkout(&git.CheckoutOptions{Hash: plumbing.NewHash(target), Force: true}),
			"checkout "+target)
	}

	var names []plumbing.ReferenceName
	switch {
	case strings.HasPrefix(target, "refs/"):
		names = []plumbing.ReferenceName{plumbing.ReferenceName(target)}
	default:
		names = []plumbing.ReferenceName{
			plumbing.NewBranchReferenceName(target), plumbing.NewTagReferenceName(target),
		}
	}
	var lastErr error
	for _, name := range names {
		// A failed attempt can leave a partial .git behind; start each try from scratch.
		_ = os.RemoveAll(filepath.Join(dest, ".git"))
		_, err := git.PlainCloneContext(ctx, dest, false, &git.CloneOptions{
			URL: url, Auth: auth, Depth: depth, ReferenceName: name, SingleBranch: true,
		})
		if err == nil {
			return nil
		}
		lastErr = err
		if ctx.Err() != nil {
			return ctx.Err()
		}
		streamer.Emit(fmt.Sprintf("ref %s not found, trying next", name), "info")
	}
	return wrapCheckout(lastErr, "clone "+target)
}

func wrapCheckout(err error, what string) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("checkout: %s: %w", what, err)
}

func buildGitURL(serverURL, owner, repo string) (string, error) {
	base, err := url.Parse(serverURL)
	if err != nil {
		return "", err
	}
	base.Path = "/git/" + owner + "/" + repo
	return base.String(), nil
}
