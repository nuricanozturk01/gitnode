package shell

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/proc"
)

const DefaultBytes = 4096

func Run(
	ctx context.Context,
	command, workDir string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
) (string, error) {

	cmd := exec.CommandContext(ctx, "bash", "-c", command) //nolint:gosec
	proc.Configure(cmd)
	cmd.Dir = workDir

	// Inject env
	env := jctx.Env()
	cmdEnv := os.Environ()
	for k, v := range env {
		cmdEnv = append(cmdEnv, k+"="+v)
	}
	cmd.Env = cmdEnv

	cmd.Stdout = streamer
	cmd.Stderr = streamer
	defer streamer.Flush()

	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) || ctx.Err() != nil {
			return "failure", nil
		}
		return "failure", fmt.Errorf("shell: run: %w", err)
	}

	return "success", nil
}
