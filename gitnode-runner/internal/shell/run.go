package shell

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

const DefaultBytes = 4096

func Run(
	ctx context.Context,
	command, workDir string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
) (string, error) {

	cmd := exec.CommandContext(ctx, "bash", "-c", command) //nolint:gosec
	cmd.Dir = workDir

	// Inject env
	env := jctx.Env()
	cmdEnv := os.Environ()
	for k, v := range env {
		cmdEnv = append(cmdEnv, k+"="+v)
	}
	cmd.Env = cmdEnv

	pr, pw := io.Pipe()
	cmd.Stdout = pw
	cmd.Stderr = pw

	if err := cmd.Start(); err != nil {
		return "failure", fmt.Errorf("shell: start: %w", err)
	}

	go func() {
		buf := make([]byte, DefaultBytes)
		for {
			n, err := pr.Read(buf)
			if n > 0 {
				lines := strings.Split(strings.TrimRight(string(buf[:n]), "\n"), "\n")
				for _, line := range lines {
					if line != "" {
						streamer.Emit(line, "info")
					}
				}
			}
			if err != nil {
				return
			}
		}
	}()

	runErr := cmd.Wait()
	if closeErr := pw.Close(); closeErr != nil && runErr == nil {
		return "failure", fmt.Errorf("shell: close pipe: %w", closeErr)
	}

	if runErr != nil {
		return "failure", nil
	}

	return "success", nil
}
