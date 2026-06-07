package actions

import (
	"context"
	"errors"
	"fmt"
	"net/url"

	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/plumbing"
	gogithttp "github.com/go-git/go-git/v5/plumbing/transport/http"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
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
func (a *CheckoutAction) Versions() []string { return []string{"v1"} }

func (a *CheckoutAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	env := jctx.Env()
	owner := env["ORIGINHUB_OWNER"]
	repo := env["ORIGINHUB_REPO"]

	if owner == "" || repo == "" {
		return nil, fmt.Errorf("checkout: ORIGINHUB_OWNER or ORIGINHUB_REPO not set in env")
	}

	ref, ok := inputs["ref"]
	if !ok || ref == "" {
		ref = env["ORIGINHUB_SHA"]
	}

	depth := 1
	if inputs["depth"] == "0" || inputs["depth"] == "full" {
		depth = 0
	}

	repoURL, err := buildGitURL(a.serverURL, owner, repo)
	if err != nil {
		return nil, fmt.Errorf("checkout: build URL: %w", err)
	}

	auth := &gogithttp.BasicAuth{Username: "x-token", Password: a.runnerToken}

	streamer.Emit(fmt.Sprintf("Cloning %s/%s ref=%s (depth=%d)", owner, repo, ref, depth), "info")

	cloneOpts := &git.CloneOptions{
		URL:   repoURL,
		Auth:  auth,
		Depth: depth,
	}

	gitRepo, cloneErr := git.PlainCloneContext(ctx, workDir, false, cloneOpts)
	if errors.Is(cloneErr, git.ErrRepositoryAlreadyExists) {
		streamer.Emit("Repository already exists, fetching latest", "info")
		var openErr error
		gitRepo, openErr = git.PlainOpen(workDir)
		if openErr != nil {
			return nil, fmt.Errorf("checkout: open existing repo: %w", openErr)
		}
		fetchErr := gitRepo.FetchContext(ctx, &git.FetchOptions{
			Auth:  auth,
			Force: true,
		})
		if fetchErr != nil && !errors.Is(fetchErr, git.NoErrAlreadyUpToDate) {
			return nil, fmt.Errorf("checkout: fetch: %w", fetchErr)
		}
	} else if cloneErr != nil {
		return nil, fmt.Errorf("checkout: clone: %w", cloneErr)
	}

	if ref != "" {
		wt, wtErr := gitRepo.Worktree()
		if wtErr != nil {
			return nil, fmt.Errorf("checkout: worktree: %w", wtErr)
		}
		checkoutErr := wt.Checkout(&git.CheckoutOptions{
			Hash:  plumbing.NewHash(ref),
			Force: true,
		})
		if checkoutErr != nil {
			return nil, fmt.Errorf("checkout: checkout ref %s: %w", ref, checkoutErr)
		}
	}

	streamer.Emit("Checkout complete", "info")
	return map[string]string{}, nil
}

func buildGitURL(serverURL, owner, repo string) (string, error) {
	base, err := url.Parse(serverURL)
	if err != nil {
		return "", err
	}
	base.Path = "/git/" + owner + "/" + repo
	return base.String(), nil
}
