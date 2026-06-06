// Package shell provides a lightweight os/exec wrapper shared by action handlers
// and the executor without creating circular imports.
package shell

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
)

// Run executes a shell command in workDir, streaming stdout/stderr to streamer.
// Returns "success" or "failure" and any exec error.
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
		buf := make([]byte, 4096)
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
	pw.Close() //nolint:errcheck

	if runErr != nil {
		return "failure", nil
	}
	return "success", nil
}
