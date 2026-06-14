package actions

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"strconv"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

// UploadArtifactAction implements actions/upload-artifact@v1.
type UploadArtifactAction struct {
	serverURL   string
	runnerToken string
}

func NewUploadArtifactAction(serverURL, runnerToken string) *UploadArtifactAction {
	return &UploadArtifactAction{serverURL: serverURL, runnerToken: runnerToken}
}

func (a *UploadArtifactAction) Name() string       { return "actions/upload-artifact" }
func (a *UploadArtifactAction) Versions() []string { return []string{"v1", "v2", "v3", "v4"} }

func (a *UploadArtifactAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	name := inputs["name"]
	if name == "" {
		return nil, fmt.Errorf("actions/upload-artifact: 'name' input is required")
	}
	artifactPath := inputs["path"]
	if artifactPath == "" {
		return nil, fmt.Errorf("actions/upload-artifact: 'path' input is required")
	}
	retentionDays := 30
	if rd, ok := inputs["retention-days"]; ok && rd != "" {
		if n, err := strconv.Atoi(rd); err == nil {
			retentionDays = n
		}
	}
	ifNoFiles := inputs["if-no-files-found"]
	if ifNoFiles == "" {
		ifNoFiles = "warn"
	}

	env := jctx.Env()
	runID := env["GITNODE_RUN_ID"]
	jobID := env["GITNODE_JOB_ID"]

	streamer.Emit(fmt.Sprintf("Uploading artifact %q from %s", name, artifactPath), "info")

	// Archive the path into a tar.gz buffer
	var buf bytes.Buffer
	if err := tarGz(artifactPath, &buf); err != nil {
		if ifNoFiles == "error" {
			return nil, fmt.Errorf("upload-artifact: failed to archive %s: %w", artifactPath, err)
		}
		streamer.Emit(fmt.Sprintf("No files found at %s: %v", artifactPath, err), ifNoFiles)
		return map[string]string{}, nil
	}

	if err := a.upload(ctx, runID, jobID, name, retentionDays, &buf, streamer); err != nil {
		return nil, err
	}

	return map[string]string{}, nil
}

func (a *UploadArtifactAction) upload(
	ctx context.Context,
	runID, jobID, name string,
	retentionDays int,
	body io.Reader,
	streamer *runnerlog.Streamer,
) error {

	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	part, err := mw.CreateFormFile("file", name+".tar.gz")
	if err != nil {
		return err
	}
	if _, copyErr := io.Copy(part, body); copyErr != nil {
		return copyErr
	}
	if closeErr := mw.Close(); closeErr != nil {
		return closeErr
	}

	endpoint, err := url.JoinPath(a.serverURL, "/api/actions/artifacts/upload")
	if err != nil {
		return err
	}

	params := url.Values{
		"runId":         {runID},
		"jobId":         {jobID},
		"name":          {name},
		"retentionDays": {strconv.Itoa(retentionDays)},
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		endpoint+"?"+params.Encode(), &buf)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+a.runnerToken)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("upload-artifact: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode >= 300 {
		return fmt.Errorf("upload-artifact: server returned %d", resp.StatusCode)
	}

	streamer.Emit(fmt.Sprintf("Artifact %q uploaded", name), "info")
	return nil
}
