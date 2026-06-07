package actions

import (
	"context"
	"fmt"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
	"github.com/nuricanozturk/originhub-runner/internal/shell"
)

// SetupGoAction implements actions/setup-go@v1.
type SetupGoAction struct{}

func NewSetupGoAction() *SetupGoAction { return &SetupGoAction{} }

func (a *SetupGoAction) Name() string       { return "actions/setup-go" }
func (a *SetupGoAction) Versions() []string { return []string{"v1"} }

func (a *SetupGoAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	version := inputs["go-version"]
	if version == "" {
		version = "1.24"
	}
	cache := inputs["cache"]
	if cache == "" {
		cache = "true"
	}

	streamer.Emit(fmt.Sprintf("Setting up Go %s", version), "info")

	// Log current go installation if present
	_, _ = shell.Run(ctx, "go version", workDir, jctx, streamer)

	// Try goenv; fall back to system Go
	script := fmt.Sprintf(
		`if command -v goenv >/dev/null 2>&1; then
			goenv install --skip-existing %s && goenv local %s
		else
			echo "goenv not found — using system Go installation"
		fi`, version, version)

	if conclusion, err := shell.Run(ctx, script, workDir, jctx, streamer); err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("setup-go: failed to set up Go %s", version)
	}

	if cache == "true" {
		streamer.Emit("Go module cache enabled (use actions/cache@v1 for cross-run persistence)", "info")
	}

	streamer.Emit(fmt.Sprintf("Go %s ready", version), "info")
	return map[string]string{"go-path": "go"}, nil
}
