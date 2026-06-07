package actions

import (
	"context"
	"fmt"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
	"github.com/nuricanozturk/originhub-runner/internal/shell"
)

// DockerLoginAction implements docker/login-action@v1.
type DockerLoginAction struct{}

func NewDockerLoginAction() *DockerLoginAction { return &DockerLoginAction{} }

func (a *DockerLoginAction) Name() string       { return "docker/login-action" }
func (a *DockerLoginAction) Versions() []string { return []string{"v1"} }

func (a *DockerLoginAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	registry := inputs["registry"]
	username := inputs["username"]
	password := inputs["password"]

	if username == "" || password == "" {
		return nil, fmt.Errorf("docker/login-action: username and password are required")
	}

	streamer.Emit(fmt.Sprintf("Logging in to Docker registry %q", registryDisplay(registry)), "info")

	// Use --password-stdin so credentials never appear in process list
	var loginCmd string
	if registry != "" {
		loginCmd = fmt.Sprintf("echo %q | docker login %s -u %s --password-stdin",
			password, registry, username)
	} else {
		loginCmd = fmt.Sprintf("echo %q | docker login -u %s --password-stdin",
			password, username)
	}

	conclusion, err := shell.Run(ctx, loginCmd, workDir, jctx, streamer)
	if err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("docker/login-action: docker login failed")
	}

	// Record logout command for post-job cleanup
	logoutCmd := "docker logout"
	if registry != "" {
		logoutCmd = "docker logout " + registry
	}
	jctx.SetEnv("ORIGINHUB_DOCKER_LOGOUT_CMD", logoutCmd)

	streamer.Emit("Docker login successful", "info")
	return map[string]string{}, nil
}

func registryDisplay(registry string) string {
	if registry == "" {
		return "docker.io"
	}
	return registry
}
