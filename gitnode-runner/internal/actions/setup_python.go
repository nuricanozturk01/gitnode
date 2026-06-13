package actions

import (
	"context"
	"fmt"
	"strings"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/shell"
)

// SetupPythonAction implements actions/setup-python@v1.
type SetupPythonAction struct{}

func NewSetupPythonAction() *SetupPythonAction { return &SetupPythonAction{} }

func (a *SetupPythonAction) Name() string       { return "actions/setup-python" }
func (a *SetupPythonAction) Versions() []string { return []string{"v1", "v2", "v3", "v4", "v5"} }

func (a *SetupPythonAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	version := inputs["python-version"]
	if version == "" {
		version = "3.12"
	}
	cache := inputs["cache"]

	streamer.Emit(fmt.Sprintf("Setting up Python %s", version), "info")

	// Try pyenv first; fall back to system python3
	script := fmt.Sprintf(
		`if command -v pyenv >/dev/null 2>&1; then
			pyenv install --skip-existing %s && pyenv local %s
		else
			echo "pyenv not found, checking system Python"
			python3 --version || true
		fi`, version, version)

	if conclusion, err := shell.Run(ctx, script, workDir, jctx, streamer); err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("setup-python: failed to set up Python %s", version)
	}

	// Handle cache setup
	switch strings.ToLower(cache) {
	case "pip":
		_, _ = shell.Run(ctx, "pip cache dir", workDir, jctx, streamer)
	case "poetry":
		if conclusion, err := shell.Run(ctx, "pip install --quiet poetry", workDir, jctx, streamer); err != nil || conclusion == "failure" {
			return nil, fmt.Errorf("setup-python: failed to install poetry")
		}
	}

	// Detect python path
	outputs := map[string]string{}
	if conclusion, err := shell.Run(ctx, "which python3", workDir, jctx, streamer); err == nil && conclusion == "success" {
		outputs["python-path"] = "python3"
	} else {
		outputs["python-path"] = "python"
	}

	streamer.Emit(fmt.Sprintf("Python %s ready", version), "info")
	return outputs, nil
}
