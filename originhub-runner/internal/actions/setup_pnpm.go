package actions

import (
	"context"
	"fmt"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
	"github.com/nuricanozturk/originhub-runner/internal/shell"
)

// SetupPnpmAction implements pnpm/action-setup@v1.
type SetupPnpmAction struct{}

func NewSetupPnpmAction() *SetupPnpmAction { return &SetupPnpmAction{} }

func (a *SetupPnpmAction) Name() string       { return "pnpm/action-setup" }
func (a *SetupPnpmAction) Versions() []string { return []string{"v1"} }

func (a *SetupPnpmAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	version := inputs["version"]
	if version == "" {
		version = "latest"
	}

	streamer.Emit(fmt.Sprintf("Setting up pnpm %s", version), "info")

	script := fmt.Sprintf(
		`if command -v corepack >/dev/null 2>&1; then
			corepack enable
			corepack prepare pnpm@%s --activate
		else
			npm install -g pnpm@%s
		fi
		pnpm --version`, version, version)

	conclusion, err := shell.Run(ctx, script, workDir, jctx, streamer)
	if err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("setup-pnpm: failed to install pnpm %s", version)
	}

	streamer.Emit(fmt.Sprintf("pnpm %s ready", version), "info")
	return map[string]string{}, nil
}
