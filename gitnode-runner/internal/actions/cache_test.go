package actions

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

func TestParseRestoreKeys_Empty(t *testing.T) {
	keys := parseRestoreKeys("")
	if len(keys) != 0 {
		t.Errorf("expected empty slice, got %v", keys)
	}
}

func TestParseRestoreKeys_MultiLine(t *testing.T) {
	raw := "maven-\ngradle-\n"
	keys := parseRestoreKeys(raw)
	if len(keys) != 2 {
		t.Errorf("expected 2 keys, got %d: %v", len(keys), keys)
	}
	if keys[0] != "maven-" || keys[1] != "gradle-" {
		t.Errorf("unexpected keys: %v", keys)
	}
}

func TestBoolStr(t *testing.T) {
	if boolStr(true) != "true" {
		t.Error("boolStr(true) should return 'true'")
	}
	if boolStr(false) != "false" {
		t.Error("boolStr(false) should return 'false'")
	}
}

// fakeCacheServer stores archives in memory and answers like the GitNode cache endpoints.
type fakeCacheServer struct {
	entries map[string][]byte // key → tar.gz
	order   []string          // keys, oldest first
}

func newFakeCacheServer(t *testing.T) (*fakeCacheServer, *httptest.Server) {
	t.Helper()
	fake := &fakeCacheServer{entries: map[string][]byte{}}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer runner-token" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		key := r.URL.Query().Get("key")
		switch r.Method {
		case http.MethodPost:
			file, _, err := r.FormFile("file")
			if err != nil {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			data, _ := io.ReadAll(file)
			if _, exists := fake.entries[key]; !exists {
				fake.order = append(fake.order, key)
			}
			fake.entries[key] = data
			w.WriteHeader(http.StatusNoContent)
		case http.MethodGet:
			matched := ""
			if _, ok := fake.entries[key]; ok {
				matched = key
			}
			for _, prefix := range r.URL.Query()["restoreKeys"] {
				for i := len(fake.order) - 1; i >= 0 && matched == ""; i-- {
					if strings.HasPrefix(fake.order[i], prefix) {
						matched = fake.order[i]
					}
				}
			}
			if matched == "" {
				w.WriteHeader(http.StatusNotFound)
				return
			}
			w.Header().Set(matchedKeyHeader, url.QueryEscape(matched))
			_, _ = w.Write(fake.entries[matched])
		}
	}))
	t.Cleanup(srv.Close)
	return fake, srv
}

// testRepoID is the repository every cache test runs for.
const testRepoID = "repo-1"

func cacheJob() *jobctx.JobContext {
	return jobctx.New(map[string]string{"GITNODE_REPO_ID": testRepoID}, nil)
}

func newTestStreamer() *runnerlog.Streamer {
	return runnerlog.New("step", nil, func(protocol.OutboundMessage) {}, zap.NewNop())
}

func TestCache_missSavesAtJobEndThenHits(t *testing.T) {
	_, srv := newFakeCacheServer(t)
	action := NewCacheAction(srv.URL, "runner-token")
	streamer := newTestStreamer()
	inputs := map[string]string{"key": "deps-v1", "path": "node_modules\n.cache/x.txt"}

	// First job: nothing cached yet.
	work1 := t.TempDir()
	jctx := cacheJob()
	out, err := action.Execute(context.Background(), inputs, jctx, streamer, work1)
	require.NoError(t, err)
	assert.Equal(t, "false", out["cache-hit"])
	assert.Equal(t, "", out["cache-matched-key"])
	assert.Equal(t, "deps-v1", out["cache-primary-key"])

	writeTree(t, work1, map[string]string{"node_modules/a/index.js": "A", ".cache/x.txt": "X"})
	post := action.PostHook(inputs, out, jctx, work1)
	require.NotNil(t, post, "a miss must save at the end of the job")
	require.NoError(t, post(context.Background(), streamer))

	// Second job: same key is an exact hit and restores the files where they were.
	work2 := t.TempDir()
	out, err = action.Execute(context.Background(), inputs, cacheJob(), streamer, work2)
	require.NoError(t, err)
	assert.Equal(t, "true", out["cache-hit"])
	assert.Equal(t, "deps-v1", out["cache-matched-key"])
	got, err := os.ReadFile(filepath.Join(work2, "node_modules", "a", "index.js"))
	require.NoError(t, err)
	assert.Equal(t, "A", string(got))
	got, err = os.ReadFile(filepath.Join(work2, ".cache", "x.txt"))
	require.NoError(t, err)
	assert.Equal(t, "X", string(got))
	assert.Nil(t, action.PostHook(inputs, out, cacheJob(), work2), "an exact hit is not saved again")
}

func TestCache_restoreKeyHitIsNotAnExactHit(t *testing.T) {
	fake, srv := newFakeCacheServer(t)
	action := NewCacheAction(srv.URL, "runner-token")
	streamer := newTestStreamer()

	seed := t.TempDir()
	writeTree(t, seed, map[string]string{"dir/f.txt": "old"})
	require.NoError(t, action.save(context.Background(), testRepoID, "deps-old",
		map[string]string{"path": "dir"}, seed, streamer))
	require.Contains(t, fake.entries, "deps-old")

	work := t.TempDir()
	jctx := cacheJob()
	inputs := map[string]string{"key": "deps-new", "path": "dir", "restore-keys": "deps-\nother-"}
	out, err := action.Execute(context.Background(), inputs, jctx, streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "false", out["cache-hit"], "only the exact key counts as a hit")
	assert.Equal(t, "deps-old", out["cache-matched-key"])
	got, err := os.ReadFile(filepath.Join(work, "dir", "f.txt"))
	require.NoError(t, err)
	assert.Equal(t, "old", string(got), "the older entry is still restored")
	assert.NotNil(t, action.PostHook(inputs, out, jctx, work), "the new key is saved at the end")
}

func TestCache_restoreOnlyAndSaveOnly(t *testing.T) {
	fake, srv := newFakeCacheServer(t)
	streamer := newTestStreamer()
	work := t.TempDir()
	writeTree(t, work, map[string]string{"out/r.txt": "r"})
	inputs := map[string]string{"key": "k1", "path": "out"}

	restore := NewCacheRestoreAction(srv.URL, "runner-token")
	out, err := restore.Execute(context.Background(), inputs, cacheJob(), streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "false", out["cache-hit"])
	assert.Nil(t, restore.PostHook(inputs, out, cacheJob(), work), "restore never saves")

	save := NewCacheSaveAction(srv.URL, "runner-token")
	_, err = save.Execute(context.Background(), inputs, cacheJob(), streamer, work)
	require.NoError(t, err)
	assert.Contains(t, fake.entries, "k1")
	assert.Equal(t, "actions/cache/restore", restore.Name())
	assert.Equal(t, "actions/cache/save", save.Name())
}

func TestCache_failOnCacheMissAndMissingInputs(t *testing.T) {
	_, srv := newFakeCacheServer(t)
	action := NewCacheAction(srv.URL, "runner-token")
	streamer := newTestStreamer()
	work := t.TempDir()

	_, err := action.Execute(context.Background(),
		map[string]string{"key": "none", "path": "x", "fail-on-cache-miss": "true"},
		cacheJob(), streamer, work)
	assert.Error(t, err)

	_, err = action.Execute(context.Background(), map[string]string{"path": "x"}, cacheJob(), streamer, work)
	assert.Error(t, err)
	_, err = action.Execute(context.Background(), map[string]string{"key": "k"}, cacheJob(), streamer, work)
	assert.Error(t, err)
}

func TestCache_withoutRepoIDIsSkippedNotFailed(t *testing.T) {
	_, srv := newFakeCacheServer(t)
	action := NewCacheAction(srv.URL, "runner-token")
	out, err := action.Execute(context.Background(), map[string]string{"key": "k", "path": "x"},
		jobctx.New(nil, nil), newTestStreamer(), t.TempDir())
	require.NoError(t, err)
	assert.Equal(t, "false", out["cache-hit"])
}

func TestCache_pathOutsideTheWorkspaceIsRestoredInPlace(t *testing.T) {
	_, srv := newFakeCacheServer(t)
	action := NewCacheAction(srv.URL, "runner-token")
	streamer := newTestStreamer()
	outside := t.TempDir() // stands for ~/.m2
	work := t.TempDir()
	writeTree(t, outside, map[string]string{"repo/lib.jar": "jar"})
	inputs := map[string]string{"key": "m2", "path": outside}

	require.NoError(t, action.save(context.Background(), testRepoID, "m2", inputs, work, streamer))
	require.NoError(t, os.RemoveAll(filepath.Join(outside, "repo")))

	out, err := action.Execute(context.Background(), inputs, cacheJob(), streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "true", out["cache-hit"])
	got, err := os.ReadFile(filepath.Join(outside, "repo", "lib.jar"))
	require.NoError(t, err)
	assert.Equal(t, "jar", string(got))
}

func TestCacheExtractPathRefusesTraversal(t *testing.T) {
	work := t.TempDir()
	_, err := cacheExtractPath(work, "../escape.txt")
	assert.Error(t, err)
	_, err = cacheExtractPath(work, absPrefix+"relative/path")
	assert.Error(t, err)
	p, err := cacheExtractPath(work, "ok/file.txt")
	require.NoError(t, err)
	assert.Equal(t, filepath.Join(work, "ok", "file.txt"), p)
}
