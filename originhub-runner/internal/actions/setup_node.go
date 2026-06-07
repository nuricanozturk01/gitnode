package actions

import (
	"context"
	"fmt"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
	"github.com/nuricanozturk/originhub-runner/internal/shell"
)

// SetupNodeAction implements actions/setup-node@v1.
// It installs the requested Node.js version via fnm, nvm, or n.
type SetupNodeAction struct{}

func NewSetupNodeAction() *SetupNodeAction { return &SetupNodeAction{} }

func (a *SetupNodeAction) Name() string       { return "actions/setup-node" }
func (a *SetupNodeAction) Versions() []string { return []string{"v1"} }

func (a *SetupNodeAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	version, ok := inputs["node-version"]
	if !ok || version == "" {
		version = "lts"
	}

	streamer.Emit(fmt.Sprintf("Setting up Node.js %s", version), "info")

	script := fmt.Sprintf(
		`if command -v fnm >/dev/null 2>&1; then
			fnm install %s && fnm use %s
		elif command -v nvm >/dev/null 2>&1; then
			nvm install %s && nvm use %s
		else
			npm install -g n && n %s
		fi
		node --version`, version, version, version, version, version)

	conclusion, err := shell.Run(ctx, script, workDir, jctx, streamer)
	if err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("setup-node: failed to install Node.js %s", version)
	}

	if cacheBackend := inputs["cache"]; cacheBackend != "" {
		streamer.Emit(fmt.Sprintf("Cache backend: %s (handled by actions/cache)", cacheBackend), "info")
	}

	streamer.Emit(fmt.Sprintf("Node.js %s ready", version), "info")
	return map[string]string{}, nil
}
