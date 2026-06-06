package actions

import (
	"context"
	"fmt"
	"runtime"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
	"github.com/nuricanozturk/originhub-runner/internal/shell"
)

// SetupJavaAction implements actions/setup-java@v1.
type SetupJavaAction struct{}

func NewSetupJavaAction() *SetupJavaAction { return &SetupJavaAction{} }

func (a *SetupJavaAction) Name() string       { return "actions/setup-java" }
func (a *SetupJavaAction) Versions() []string { return []string{"v1"} }

func (a *SetupJavaAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	javaVersion := inputs["java-version"]
	if javaVersion == "" {
		javaVersion = "21"
	}
	distribution := inputs["distribution"]
	if distribution == "" {
		distribution = "temurin"
	}

	streamer.Emit(fmt.Sprintf("Setting up Java %s (%s)", javaVersion, distribution), "info")

	sdkManID := distributionToSdkManId(distribution, javaVersion, runtime.GOARCH)
	script := fmt.Sprintf(
		`if command -v sdk >/dev/null 2>&1; then
			sdk install java %s || true && sdk use java %s
		elif java -version 2>&1 | grep -q '%s'; then
			echo "Java %s already available"
		else
			curl -s "https://get.sdkman.io" | bash
			source "$HOME/.sdkman/bin/sdkman-init.sh"
			sdk install java %s && sdk use java %s
		fi
		java -version`,
		sdkManID, sdkManID, javaVersion, javaVersion, sdkManID, sdkManID)

	conclusion, err := shell.Run(ctx, script, workDir, jctx, streamer)
	if err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("setup-java: failed to install Java %s (%s)", javaVersion, distribution)
	}

	streamer.Emit(fmt.Sprintf("Java %s ready", javaVersion), "info")
	return map[string]string{}, nil
}

func distributionToSdkManId(distribution, version, _ string) string {
	switch distribution {
	case "corretto":
		return version + ".0-amzn"
	case "zulu":
		return version + ".0-zulu"
	default:
		return version + ".0-tem"
	}
}
