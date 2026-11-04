package executor

import (
	"context"
	"fmt"
	"io"
	"path"
	"path/filepath"
	"strings"

	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/mount"
	"github.com/docker/docker/client"
	"go.uber.org/zap"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

const defaultImage = "ubuntu:24.04"

// DockerExecutor runs steps inside a Docker container.
type DockerExecutor struct {
	workDir string
	image   string
	log     *zap.Logger
	shell   string
	subDir  string
	// sharedPaths are host directories bind-mounted at the identical path inside the container
	// (the runtime files behind $GITHUB_OUTPUT, $GITHUB_ENV, ...).
	sharedPaths []string
}

// NewDockerExecutor returns a DockerExecutor.
// image is the default container image; steps may override this.
func NewDockerExecutor(workDir, image string, log *zap.Logger) *DockerExecutor {
	if image == "" {
		image = defaultImage
	}
	return &DockerExecutor{workDir: workDir, image: image, log: log}
}

// WithShell selects the interpreter; empty means GitHub's default.
func (e *DockerExecutor) WithShell(shell string) *DockerExecutor {
	e.shell = shell
	return e
}

// WithSubDir sets the working directory below the mounted workspace.
func (e *DockerExecutor) WithSubDir(subDir string) *DockerExecutor {
	e.subDir = subDir
	return e
}

// WithSharedPaths mounts host paths at the same location inside the container.
func (e *DockerExecutor) WithSharedPaths(paths ...string) *DockerExecutor {
	e.sharedPaths = append(e.sharedPaths, paths...)
	return e
}

// Run executes a shell command inside a Docker container, streaming output via streamer.
func (e *DockerExecutor) Run(
	ctx context.Context,
	command string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
) (conclusion string, err error) {

	cli, err := client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	if err != nil {
		return "failure", fmt.Errorf("docker client: %w", err)
	}
	defer cli.Close() //nolint:errcheck

	args, argsErr := shellArgs(e.shell, command)
	if argsErr != nil {
		return "failure", argsErr
	}

	envVars := make([]string, 0)
	for k, v := range jctx.Env() {
		if k == "GITHUB_WORKSPACE" {
			v = "/workspace" // the host path does not exist inside the container
		}
		envVars = append(envVars, k+"="+v)
	}

	workingDir := "/workspace"
	if e.subDir != "" && e.subDir != "." {
		workingDir = path.Join("/workspace", filepath.ToSlash(e.subDir))
	}

	cfg := &container.Config{
		Image:        e.image,
		Cmd:          args,
		Env:          envVars,
		WorkingDir:   workingDir,
		AttachStdout: true,
		AttachStderr: true,
	}
	mounts := []mount.Mount{{Type: mount.TypeBind, Source: e.workDir, Target: "/workspace"}}
	for _, p := range e.sharedPaths {
		mounts = append(mounts, mount.Mount{Type: mount.TypeBind, Source: p, Target: p})
	}
	hostCfg := &container.HostConfig{
		Mounts:      mounts,
		SecurityOpt: []string{"no-new-privileges"},
	}

	streamer.Emit(fmt.Sprintf("[docker] $ %s", strings.TrimSpace(command)), "info")

	resp, err := cli.ContainerCreate(ctx, cfg, hostCfg, nil, nil, "")
	if err != nil {
		return "failure", fmt.Errorf("container create: %w", err)
	}
	containerID := resp.ID

	defer func() {
		rmCtx := context.Background()
		if rmErr := cli.ContainerRemove(
			rmCtx, containerID, container.RemoveOptions{Force: true},
		); rmErr != nil {
			e.log.Warn("failed to remove container", zap.String("id", containerID), zap.Error(rmErr))
		}
	}()

	if startErr := cli.ContainerStart(ctx, containerID, container.StartOptions{}); startErr != nil {
		return "failure", fmt.Errorf("container start: %w", startErr)
	}

	logReader, err := cli.ContainerLogs(
		ctx, containerID,
		container.LogsOptions{ShowStdout: true, ShowStderr: true, Follow: true},
	)
	if err != nil {
		return "failure", fmt.Errorf("container logs: %w", err)
	}
	defer logReader.Close() //nolint:errcheck

	if _, err := io.Copy(streamer, logReader); err != nil && ctx.Err() == nil {
		e.log.Warn("log copy interrupted", zap.Error(err))
	}

	statusCh, errCh := cli.ContainerWait(ctx, containerID, container.WaitConditionNotRunning)
	select {
	case status := <-statusCh:
		if status.StatusCode != 0 {
			streamer.Emit(fmt.Sprintf("Container exited with code %d", status.StatusCode), "error")
			return "failure", nil
		}
		return "success", nil
	case err := <-errCh:
		if ctx.Err() != nil {
			return "cancelled", nil
		}
		return "failure", fmt.Errorf("container wait: %w", err)
	}
}
