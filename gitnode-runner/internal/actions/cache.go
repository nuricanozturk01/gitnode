package actions

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

// absPrefix marks archive entries that live outside the workspace (for example ~/.npm); the rest of
// the name is the absolute path they are restored to.
const absPrefix = "__abs__"

// matchedKeyHeader is how the server tells which key a cache hit was stored under.
const matchedKeyHeader = "X-Cache-Matched-Key"

type cacheMode int

const (
	cacheRestoreAndSave cacheMode = iota // actions/cache
	cacheRestoreOnly                     // actions/cache/restore
	cacheSaveOnly                        // actions/cache/save
)

// CacheAction implements actions/cache, actions/cache/restore and actions/cache/save.
//
// actions/cache restores an archive for the key (or the newest one whose key starts with a
// restore-key) before the following steps run and, when there was no exact hit, saves the paths as
// a new archive when the job ends successfully.
type CacheAction struct {
	serverURL   string
	runnerToken string
	mode        cacheMode
}

func NewCacheAction(serverURL, runnerToken string) *CacheAction {
	return &CacheAction{serverURL: serverURL, runnerToken: runnerToken, mode: cacheRestoreAndSave}
}

func NewCacheRestoreAction(serverURL, runnerToken string) *CacheAction {
	return &CacheAction{serverURL: serverURL, runnerToken: runnerToken, mode: cacheRestoreOnly}
}

func NewCacheSaveAction(serverURL, runnerToken string) *CacheAction {
	return &CacheAction{serverURL: serverURL, runnerToken: runnerToken, mode: cacheSaveOnly}
}

func (a *CacheAction) Name() string {
	switch a.mode {
	case cacheRestoreOnly:
		return "actions/cache/restore"
	case cacheSaveOnly:
		return "actions/cache/save"
	}
	return "actions/cache"
}

func (a *CacheAction) Versions() []string { return []string{"v1", "v2", "v3", "v4"} }

func (a *CacheAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	key := strings.TrimSpace(inputs["key"])
	if key == "" {
		return nil, fmt.Errorf("%s: 'key' input is required", a.Name())
	}
	if strings.TrimSpace(inputs["path"]) == "" {
		return nil, fmt.Errorf("%s: 'path' input is required", a.Name())
	}
	repoID := jctx.Env()["GITNODE_REPO_ID"]
	if repoID == "" {
		streamer.Emit("The server did not say which repository this job belongs to; the cache is skipped", "warn")
		return map[string]string{"cache-hit": "false", "cache-primary-key": key, "cache-matched-key": ""}, nil
	}

	if a.mode == cacheSaveOnly {
		if err := a.save(ctx, repoID, key, inputs, workDir, streamer); err != nil {
			return nil, err
		}
		return map[string]string{}, nil
	}
	return a.restoreStep(ctx, repoID, key, inputs, workDir, streamer)
}

func (a *CacheAction) restoreStep(
	ctx context.Context,
	repoID, key string,
	inputs map[string]string,
	workDir string,
	streamer *runnerlog.Streamer,
) (map[string]string, error) {

	streamer.Emit(fmt.Sprintf("Cache restore for key: %s", key), "info")
	matched, err := a.restore(ctx, repoID, key, parseRestoreKeys(inputs["restore-keys"]), workDir,
		strings.EqualFold(inputs["lookup-only"], "true"))
	if err != nil {
		streamer.Emit(fmt.Sprintf("Cache restore failed: %v", err), "warn")
	}

	outputs := map[string]string{
		"cache-hit":         boolStr(matched == key),
		"cache-primary-key": key,
		"cache-matched-key": matched,
	}
	switch matched {
	case "":
		if strings.EqualFold(inputs["fail-on-cache-miss"], "true") {
			return outputs, fmt.Errorf("cache not found for key: %s", key)
		}
		streamer.Emit("Cache not found for the input keys", "info")
	case key:
		streamer.Emit(fmt.Sprintf("Cache restored from key: %s", matched), "info")
	default:
		streamer.Emit(fmt.Sprintf("Cache restored from restore key: %s", matched), "info")
	}
	return outputs, nil
}

// PostHook saves the cache when the job ends, unless the exact key was restored.
func (a *CacheAction) PostHook(
	inputs, outputs map[string]string,
	jctx *jobctx.JobContext,
	workDir string,
) func(ctx context.Context, streamer *runnerlog.Streamer) error {

	repoID := jctx.Env()["GITNODE_REPO_ID"]
	if a.mode != cacheRestoreAndSave || repoID == "" || outputs["cache-hit"] == "true" ||
		strings.EqualFold(inputs["lookup-only"], "true") {
		return nil
	}
	key := strings.TrimSpace(inputs["key"])
	return func(ctx context.Context, streamer *runnerlog.Streamer) error {
		return a.save(ctx, repoID, key, inputs, workDir, streamer)
	}
}

// save archives the paths and uploads them under key. Nothing to archive is not an error.
func (a *CacheAction) save(
	ctx context.Context,
	repoID, key string,
	inputs map[string]string,
	workDir string,
	streamer *runnerlog.Streamer,
) error {

	files, _, err := expandPatterns(workDir, inputs["path"])
	if err != nil {
		streamer.Emit("Path validation: the paths for caching do not exist, so no cache is saved", "warn")
		return nil
	}
	entries := make([]artifactFile, 0, len(files))
	for _, file := range files {
		info, statErr := os.Stat(filepath.FromSlash(file))
		if statErr != nil || !info.Mode().IsRegular() {
			continue
		}
		entries = append(entries, artifactFile{
			abs: filepath.FromSlash(file), rel: cacheEntryName(workDir, file),
			mode: info.Mode().Perm(), size: info.Size(),
		})
	}
	if len(entries) == 0 {
		streamer.Emit("No files to cache", "warn")
		return nil
	}

	var archive bytes.Buffer
	if err := tarGzFiles(entries, &archive); err != nil {
		return fmt.Errorf("cache save: archive: %w", err)
	}
	streamer.Emit(fmt.Sprintf("Saving cache for key: %s (%d files)", key, len(entries)), "info")
	return a.upload(ctx, repoID, key, parseRestoreKeys(inputs["restore-keys"]), &archive, streamer)
}

// cacheEntryName is the name of a file inside the archive: relative to the workspace when it is
// inside it, otherwise absPrefix plus its absolute path.
func cacheEntryName(workDir, file string) string {
	root := filepath.ToSlash(filepath.Clean(workDir))
	if rel, ok := strings.CutPrefix(file, root+"/"); ok {
		return rel
	}
	return absPrefix + file
}

func (a *CacheAction) upload(
	ctx context.Context,
	repoID, key string,
	restoreKeys []string,
	archive io.Reader,
	streamer *runnerlog.Streamer,
) error {

	var body bytes.Buffer
	mw := multipart.NewWriter(&body)
	part, err := mw.CreateFormFile("file", "cache.tar.gz")
	if err != nil {
		return err
	}
	if _, copyErr := io.Copy(part, archive); copyErr != nil {
		return copyErr
	}
	if closeErr := mw.Close(); closeErr != nil {
		return closeErr
	}

	endpoint, err := url.JoinPath(a.serverURL, "/api/actions/cache")
	if err != nil {
		return fmt.Errorf("cache save: build URL: %w", err)
	}
	params := url.Values{"repoId": {repoID}, "key": {key}}
	for _, rk := range restoreKeys {
		params.Add("restoreKeys", rk)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint+"?"+params.Encode(), &body)
	if err != nil {
		return fmt.Errorf("cache save: build request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+a.runnerToken)
	req.Header.Set("Content-Type", mw.FormDataContentType())

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("cache save: %w", err)
	}
	defer resp.Body.Close() //nolint:errcheck
	if resp.StatusCode >= 300 {
		return fmt.Errorf("cache save: server returned %d", resp.StatusCode)
	}
	streamer.Emit(fmt.Sprintf("Cache saved with key: %s", key), "info")
	return nil
}

// restore downloads the archive for key (or the newest one a restore key matches) and extracts
// it. It returns the key the archive was stored under, or "" on a miss.
func (a *CacheAction) restore(
	ctx context.Context,
	repoID, key string,
	restoreKeys []string,
	workDir string,
	lookupOnly bool,
) (string, error) {

	endpoint, err := url.JoinPath(a.serverURL, "/api/actions/cache")
	if err != nil {
		return "", fmt.Errorf("build cache URL: %w", err)
	}
	params := url.Values{"repoId": {repoID}, "key": {key}}
	for _, rk := range restoreKeys {
		params.Add("restoreKeys", rk)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint+"?"+params.Encode(), nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", "Bearer "+a.runnerToken)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close() //nolint:errcheck

	if resp.StatusCode == http.StatusNotFound {
		return "", nil
	}
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("server returned %d", resp.StatusCode)
	}

	matched := key
	if header := resp.Header.Get(matchedKeyHeader); header != "" {
		if decoded, decErr := url.QueryUnescape(header); decErr == nil {
			matched = decoded
		}
	}
	if lookupOnly {
		return matched, nil
	}
	if err := extractTarGz(resp.Body, func(name string) (string, error) {
		return cacheExtractPath(workDir, name)
	}); err != nil {
		return "", err
	}
	return matched, nil
}

// cacheExtractPath says where an archive entry is restored to.
func cacheExtractPath(workDir, name string) (string, error) {
	if rest, ok := strings.CutPrefix(name, absPrefix); ok {
		clean := filepath.Clean(filepath.FromSlash(rest))
		if !filepath.IsAbs(clean) {
			return "", fmt.Errorf("archive entry %q is not an absolute path", name)
		}
		return clean, nil
	}
	return safeExtractPath(workDir, name)
}

// unTarGz extracts a tar.gz below dest.
func unTarGz(r io.Reader, dest string) error {
	return extractTarGz(r, func(name string) (string, error) { return safeExtractPath(dest, name) })
}

// extractTarGz unpacks a tar.gz; resolve decides where each entry goes (and may refuse it).
// Only regular files and directories are restored, with their permissions.
func extractTarGz(r io.Reader, resolve func(name string) (string, error)) error {
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
		target, resolveErr := resolve(hdr.Name)
		if resolveErr != nil {
			return resolveErr
		}
		switch hdr.Typeflag {
		case tar.TypeDir:
			if mkdirErr := os.MkdirAll(target, 0o750); mkdirErr != nil {
				return mkdirErr
			}
		case tar.TypeReg:
			if writeErr := writeEntry(target, tr, hdr.FileInfo().Mode().Perm()); writeErr != nil {
				return writeErr
			}
		}
	}
}

func writeEntry(target string, content io.Reader, perm os.FileMode) error {
	if perm == 0 {
		perm = 0o644
	}
	if err := os.MkdirAll(filepath.Dir(target), 0o750); err != nil {
		return err
	}
	f, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, perm) //nolint:gosec
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(f, content) //nolint:gosec
	closeErr := f.Close()
	if copyErr != nil {
		return copyErr
	}
	if closeErr != nil {
		return closeErr
	}
	return os.Chmod(target, perm)
}

// safeExtractPath joins an archive entry name to dest and refuses names that climb out of it.
func safeExtractPath(dest, name string) (string, error) {
	target := filepath.Join(dest, filepath.FromSlash(name))
	rel, err := filepath.Rel(dest, target)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) ||
		filepath.IsAbs(name) {
		return "", fmt.Errorf("archive entry %q escapes the destination", name)
	}
	return target, nil
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
