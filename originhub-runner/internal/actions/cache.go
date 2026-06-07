package actions

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
)

// CacheAction implements actions/cache@v1.
// Restores a tar.gz cache from the server before the job steps run,
// and uploads a new cache archive after they finish.
type CacheAction struct {
	serverURL   string
	runnerToken string
}

func NewCacheAction(serverURL, runnerToken string) *CacheAction {
	return &CacheAction{serverURL: serverURL, runnerToken: runnerToken}
}

func (a *CacheAction) Name() string       { return "actions/cache" }
func (a *CacheAction) Versions() []string { return []string{"v1"} }

func (a *CacheAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	key := inputs["key"]
	if key == "" {
		return nil, fmt.Errorf("actions/cache: 'key' input is required")
	}
	cachePath := inputs["path"]
	if cachePath == "" {
		return nil, fmt.Errorf("actions/cache: 'path' input is required")
	}
	restoreKeys := parseRestoreKeys(inputs["restore-keys"])
	repoID := jctx.Env()["ORIGINHUB_REPO_ID"]

	streamer.Emit(fmt.Sprintf("Restoring cache for key: %s", key), "info")

	hit, err := a.restore(ctx, repoID, key, restoreKeys, cachePath, workDir)
	if err != nil {
		streamer.Emit(fmt.Sprintf("Cache restore warning: %v", err), "warn")
	}

	outputs := map[string]string{"cache-hit": boolStr(hit)}
	if hit {
		streamer.Emit("Cache restored successfully", "info")
	} else {
		streamer.Emit("Cache miss — will upload after job", "info")
		// Store metadata so post-job steps can upload
		jctx.SetEnv("ORIGINHUB_CACHE_KEY", key)
		jctx.SetEnv("ORIGINHUB_CACHE_PATH", cachePath)
		jctx.SetEnv("ORIGINHUB_CACHE_REPO_ID", repoID)
	}
	return outputs, nil
}

// Upload tarballs cachePath and posts it to the server.
func (a *CacheAction) Upload(
	ctx context.Context,
	repoID, key, cachePath string,
	streamer *runnerlog.Streamer,
) error {

	streamer.Emit(fmt.Sprintf("Uploading cache for key: %s", key), "info")

	pr, pw := io.Pipe()
	go func() {
		pw.CloseWithError(tarGz(cachePath, pw))
	}()

	endpoint, err := url.JoinPath(a.serverURL, "/api/actions/cache")
	if err != nil {
		return fmt.Errorf("cache upload: build URL: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		endpoint+"?repoId="+repoID+"&key="+url.QueryEscape(key), pr)
	if err != nil {
		return fmt.Errorf("cache upload: build request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+a.runnerToken)
	req.Header.Set("Content-Type", "application/gzip")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("cache upload: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode >= 300 {
		return fmt.Errorf("cache upload: server returned %d", resp.StatusCode)
	}
	streamer.Emit("Cache uploaded", "info")
	return nil
}

// ── restore ───────────────────────────────────────────────────────────────────

func (a *CacheAction) restore(
	ctx context.Context,
	repoID, key string,
	restoreKeys []string,
	cachePath, workDir string,
) (bool, error) {

	endpoint, err := url.JoinPath(a.serverURL, "/api/actions/cache")
	if err != nil {
		return false, fmt.Errorf("build cache URL: %w", err)
	}

	params := url.Values{}
	params.Set("repoId", repoID)
	params.Set("key", key)
	for _, rk := range restoreKeys {
		params.Add("restoreKeys", rk)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		endpoint+"?"+params.Encode(), nil)
	if err != nil {
		return false, err
	}
	req.Header.Set("Authorization", "Bearer "+a.runnerToken)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode == http.StatusNotFound {
		return false, nil
	}
	if resp.StatusCode >= 300 {
		return false, fmt.Errorf("server returned %d", resp.StatusCode)
	}

	target := filepath.Join(workDir, cachePath)
	if err := os.MkdirAll(target, 0o750); err != nil {
		return false, err
	}
	if err := unTarGz(resp.Body, target); err != nil {
		return false, err
	}
	return true, nil
}

// ── tar helpers ───────────────────────────────────────────────────────────────

func tarGz(src string, w io.Writer) error {
	root, err := os.OpenRoot(src)
	if err != nil {
		return err
	}
	defer root.Close() //nolint:errcheck

	gw := gzip.NewWriter(w)
	tw := tar.NewWriter(gw)

	err = filepath.WalkDir(src, func(path string, info os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		fi, statErr := info.Info()
		if statErr != nil {
			return statErr
		}
		hdr, headerErr := tar.FileInfoHeader(fi, "")
		if headerErr != nil {
			return headerErr
		}
		rel, relErr := filepath.Rel(src, path)
		if relErr != nil {
			return relErr
		}
		hdr.Name = rel
		if writeErr := tw.WriteHeader(hdr); writeErr != nil {
			return writeErr
		}
		if fi.IsDir() {
			return nil
		}
		f, openErr := root.Open(rel)
		if openErr != nil {
			return openErr
		}
		_, copyErr := io.Copy(tw, f)
		closeErr := f.Close()
		if copyErr != nil {
			return copyErr
		}
		return closeErr
	})
	if err != nil {
		return err
	}
	if err := tw.Close(); err != nil {
		return err
	}
	return gw.Close()
}

func unTarGz(r io.Reader, dest string) error {
	gr, err := gzip.NewReader(r)
	if err != nil {
		return err
	}
	defer gr.Close() //nolint:errcheck
	tr := tar.NewReader(gr)
	for {
		hdr, err := tr.Next()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}
		target := filepath.Join(dest, hdr.Name) //nolint:gosec
		if hdr.Typeflag == tar.TypeDir {
			if mkdirErr := os.MkdirAll(target, 0o750); mkdirErr != nil {
				return mkdirErr
			}
			continue
		}
		f, err := os.Create(target) //nolint:gosec
		if err != nil {
			return err
		}
		_, copyErr := io.Copy(f, tr) //nolint:gosec
		closeErr := f.Close()
		if copyErr != nil {
			return copyErr
		}
		if closeErr != nil {
			return closeErr
		}
	}
}

func parseRestoreKeys(raw string) []string {
	if raw == "" {
		return nil
	}
	var keys []string
	for _, line := range strings.Split(raw, "\n") {
		if k := strings.TrimSpace(line); k != "" {
			keys = append(keys, k)
		}
	}
	return keys
}

func boolStr(b bool) string {
	if b {
		return "true"
	}
	return "false"
}
