package executor

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

// ShellExecutor runs step commands directly in the host shell.
type ShellExecutor struct {
	workDir string
	shell   string
}

// NewShellExecutor returns a ShellExecutor rooted at workDir.
func NewShellExecutor(workDir string) *ShellExecutor {
	return &ShellExecutor{workDir: workDir}
}

// WithShell selects the interpreter (bash, sh, python, pwsh); empty means GitHub's default.
func (e *ShellExecutor) WithShell(shell string) *ShellExecutor {
	e.shell = shell
	return e
}

// Run executes a shell command string, streaming output via streamer.
// Returns a non-nil error only for system-level failures; non-zero exit codes
// are reported through conclusion "failure".
func (e *ShellExecutor) Run(
	ctx context.Context,
	command string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
) (conclusion string, err error) {

	args, argsErr := shellArgs(e.shell, command)
	if argsErr != nil {
		return "failure", argsErr
	}
	cmd := exec.CommandContext(ctx, args[0], args[1:]...) //nolint:gosec
	cmd.Dir = e.workDir

	// Inject env vars from job context on top of the inherited OS env.
	env := os.Environ()
	for k, v := range jctx.Env() {
		env = append(env, k+"="+v)
	}
	cmd.Env = env

	// Pipe stdout+stderr through the streamer.
	cmd.Stdout = streamer
	cmd.Stderr = &prefixWriter{prefix: "[stderr] ", w: streamer}

	streamer.Emit(fmt.Sprintf("$ %s", strings.TrimSpace(command)), "info")

	if err := cmd.Run(); err != nil {
		if ctx.Err() != nil {
			return "cancelled", nil
		}
		streamer.Emit(fmt.Sprintf("Command exited with error: %v", err), "error")
		return "failure", nil
	}

	return "success", nil
}

// shellArgs builds the interpreter command line the way GitHub does: without an explicit shell,
// bash runs with -e (stop at the first failing command); `shell: bash` additionally enables
// pipefail.
func shellArgs(shell, command string) ([]string, error) {
	switch strings.ToLower(strings.TrimSpace(shell)) {
	case "":
		return []string{"bash", "-e", "-c", command}, nil
	case "bash":
		return []string{"bash", "--noprofile", "--norc", "-eo", "pipefail", "-c", command}, nil
	case "sh":
		return []string{"sh", "-e", "-c", command}, nil
	case "python", "python3":
		return []string{"python3", "-c", command}, nil
	case "pwsh":
		return []string{"pwsh", "-NoProfile", "-Command", command}, nil
	}
	return nil, fmt.Errorf("unsupported shell %q (supported: bash, sh, python, pwsh)", shell)
}

// prefixWriter prepends a string to every line written to the underlying writer.
type prefixWriter struct {
	prefix string
	w      io.Writer
}

func (p *prefixWriter) Write(b []byte) (int, error) {
	lines := strings.Split(strings.TrimRight(string(b), "\n"), "\n")
	for _, line := range lines {
		if _, err := fmt.Fprintf(p.w, "%s%s\n", p.prefix, line); err != nil {
			return 0, err
		}
	}
	return len(b), nil
}
