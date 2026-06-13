package actions

import (
	"context"
	"fmt"
	"strings"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/shell"
)

// SetupBuildxAction implements docker/setup-buildx-action@v1/v2/v3.
// Ensures Docker Buildx is available, creates an isolated builder instance,
// and optionally sets it as the current builder.
type SetupBuildxAction struct{}

func NewSetupBuildxAction() *SetupBuildxAction { return &SetupBuildxAction{} }

func (a *SetupBuildxAction) Name() string       { return "docker/setup-buildx-action" }
func (a *SetupBuildxAction) Versions() []string { return []string{"v1", "v2", "v3"} }

func (a *SetupBuildxAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	driver := inputs["driver"]
	if driver == "" {
		driver = "docker-container"
	}
	builderName := inputs["name"]
	if builderName == "" {
		builderName = "gitnode-builder"
	}
	// "use" defaults to true in the upstream action
	use := inputs["use"] != "false"

	if v := inputs["version"]; v != "" {
		streamer.Emit(fmt.Sprintf("Note: specific buildx version %q not installable on GitNode runners — using available version", v), "warning")
	}

	streamer.Emit("Setting up Docker Buildx", "info")

	// Verify buildx is available
	if conclusion, err := shell.Run(ctx, "docker buildx version", workDir, jctx, streamer); err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("docker/setup-buildx-action: docker buildx is not available on this runner")
	}

	// Create builder if it does not exist; reuse if it does
	createScript := fmt.Sprintf(
		`if docker buildx inspect %s >/dev/null 2>&1; then
			echo "Builder %s already exists, reusing"
		else
			docker buildx create --name %s --driver %s --bootstrap
		fi`,
		builderName, builderName, builderName, driver,
	)
	if conclusion, err := shell.Run(ctx, createScript, workDir, jctx, streamer); err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("docker/setup-buildx-action: failed to create builder %q", builderName)
	}

	if use {
		useCmd := fmt.Sprintf("docker buildx use %s", builderName)
		if conclusion, err := shell.Run(ctx, useCmd, workDir, jctx, streamer); err != nil || conclusion == "failure" {
			return nil, fmt.Errorf("docker/setup-buildx-action: failed to activate builder %q", builderName)
		}
	}

	// install: true makes plain `docker build` route through buildx
	if inputs["install"] == "true" {
		if _, err := shell.Run(ctx, "docker buildx install", workDir, jctx, streamer); err != nil {
			streamer.Emit("Warning: docker buildx install failed (non-fatal)", "warning")
		}
	}

	if platforms := inputs["platforms"]; platforms != "" {
		streamer.Emit(fmt.Sprintf("Buildx supported platforms: %s", platforms), "info")
	}

	// Bootstrap and print builder info
	if conclusion, err := shell.Run(ctx, "docker buildx inspect --bootstrap", workDir, jctx, streamer); err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("docker/setup-buildx-action: builder bootstrap failed")
	}

	streamer.Emit(fmt.Sprintf("Docker Buildx builder %q is ready", builderName), "info")
	return map[string]string{"name": strings.TrimSpace(builderName)}, nil
}
