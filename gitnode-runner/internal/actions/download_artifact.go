package actions

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path/filepath"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

// DownloadArtifactAction implements actions/download-artifact@v1.
type DownloadArtifactAction struct {
	serverURL   string
	runnerToken string
}

func NewDownloadArtifactAction(serverURL, runnerToken string) *DownloadArtifactAction {
	return &DownloadArtifactAction{serverURL: serverURL, runnerToken: runnerToken}
}

func (a *DownloadArtifactAction) Name() string       { return "actions/download-artifact" }
func (a *DownloadArtifactAction) Versions() []string { return []string{"v1", "v2", "v3", "v4"} }

func (a *DownloadArtifactAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	name := inputs["name"]
	if name == "" {
		return nil, fmt.Errorf("actions/download-artifact: 'name' input is required")
	}
	destPath := inputs["path"]
	if destPath == "" {
		destPath = filepath.Join(workDir, name)
	}

	env := jctx.Env()
	runID := env["GITNODE_RUN_ID"]

	streamer.Emit(fmt.Sprintf("Downloading artifact %q to %s", name, destPath), "info")

	if err := os.MkdirAll(destPath, 0o750); err != nil {
		return nil, fmt.Errorf("download-artifact: create dest dir: %w", err)
	}

	if err := a.download(ctx, runID, name, destPath, streamer); err != nil {
		return nil, err
	}

	return map[string]string{}, nil
}

func (a *DownloadArtifactAction) download(
	ctx context.Context,
	runID, name, destPath string,
	streamer *runnerlog.Streamer,
) error {

	endpoint, err := url.JoinPath(a.serverURL, "/api/actions/artifacts", runID, name)
	if err != nil {
		return fmt.Errorf("download-artifact: build URL: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+a.runnerToken)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("download-artifact: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode == http.StatusNotFound {
		return fmt.Errorf("download-artifact: artifact %q not found for run %s", name, runID)
	}
	if resp.StatusCode >= 300 {
		return fmt.Errorf("download-artifact: server returned %d", resp.StatusCode)
	}

	if err := unTarGz(resp.Body, destPath); err != nil {
		return fmt.Errorf("download-artifact: extract: %w", err)
	}

	streamer.Emit(fmt.Sprintf("Artifact %q extracted to %s", name, destPath), "info")
	return nil
}
