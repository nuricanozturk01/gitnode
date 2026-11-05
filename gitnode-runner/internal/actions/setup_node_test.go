package actions

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

const nodeIndex = `[
 {"version":"v27.0.0-alpha.1","lts":false},
 {"version":"v26.1.0","lts":false},
 {"version":"v24.10.0","lts":"Krypton"},
 {"version":"v24.2.1","lts":"Krypton"},
 {"version":"v22.9.0","lts":"Jod"},
 {"version":"v20.11.1","lts":"Iron"}
]`

func releases() []nodeRelease {
	return []nodeRelease{
		{Version: "v27.0.0-alpha.1", LTS: false},
		{Version: "v26.1.0", LTS: false},
		{Version: "v24.10.0", LTS: "Krypton"},
		{Version: "v24.2.1", LTS: "Krypton"},
		{Version: "v22.9.0", LTS: "Jod"},
		{Version: "v20.11.1", LTS: "Iron"},
	}
}

func TestPickNodeRelease(t *testing.T) {
	cases := map[string]string{
		"latest":  "v26.1.0", // never the pre-release at the top
		"current": "v26.1.0",
		"lts":     "v24.10.0",
		"lts/*":   "v24.10.0",
		"lts/jod": "v22.9.0",
		"24":      "v24.10.0",
		"24.2":    "v24.2.1",
		"24.x":    "v24.10.0",
		"^22":     "v22.9.0",
		"v20":     "v20.11.1",
		"18":      "",
	}
	for spec, want := range cases {
		assert.Equal(t, want, pickNodeRelease(releases(), normalizeNodeSpec(spec)), spec)
	}
}

func TestNodeMatches(t *testing.T) {
	assert.True(t, nodeMatches("24", "v24.2.1"))
	assert.True(t, nodeMatches("24.2", "24.2.1"))
	assert.True(t, nodeMatches("24.2.1", "v24.2.1"))
	assert.False(t, nodeMatches("2", "v24.2.1"), "2 is not 24")
	assert.False(t, nodeMatches("24.2", "v24.20.0"))
	assert.False(t, nodeMatches("lts", "v24.2.1"))
}

func TestCompareVersions(t *testing.T) {
	assert.Positive(t, compareVersions("24.10.0", "24.2.1"), "numeric, not alphabetical")
	assert.Negative(t, compareVersions("22.9.0", "24.0.0"))
	assert.Zero(t, compareVersions("24.2.1", "24.2.1"))
}

func TestArchiveURL(t *testing.T) {
	a := NewSetupNodeAction()
	assert.Equal(t, "https://nodejs.org/dist/v24.2.1/node-v24.2.1-linux-x64.tar.gz",
		a.archiveURL("v24.2.1", "linux-x64"))
	assert.Equal(t, "https://unofficial-builds.nodejs.org/download/release/v24.2.1/node-v24.2.1-linux-x64-musl.tar.gz",
		a.archiveURL("v24.2.1", "linux-x64-musl"), "Alpine needs the musl build")
}

// nodeTarball is a release archive whose node and npm just print their version.
func nodeTarball(t *testing.T, version string) []byte {
	t.Helper()
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	tw := tar.NewWriter(gz)
	top := "node-" + version + "-x/"
	add := func(h *tar.Header, body string) {
		h.Name = top + h.Name
		h.Size = int64(len(body))
		require.NoError(t, tw.WriteHeader(h))
		_, err := tw.Write([]byte(body))
		require.NoError(t, err)
	}
	require.NoError(t, tw.WriteHeader(&tar.Header{Name: top, Typeflag: tar.TypeDir, Mode: 0o755}))
	require.NoError(t, tw.WriteHeader(&tar.Header{Name: top + "bin/", Typeflag: tar.TypeDir, Mode: 0o755}))
	add(&tar.Header{Name: "bin/node", Typeflag: tar.TypeReg, Mode: 0o755}, "#!/bin/sh\necho "+version+"\n")
	add(&tar.Header{Name: "lib/npm-cli.sh", Typeflag: tar.TypeReg, Mode: 0o755}, "#!/bin/sh\necho 10.0.0\n")
	require.NoError(t, tw.WriteHeader(&tar.Header{Name: top + "bin/npm", Typeflag: tar.TypeSymlink, Linkname: "../lib/npm-cli.sh"}))
	require.NoError(t, tw.Close())
	require.NoError(t, gz.Close())
	return buf.Bytes()
}

type fakeNodejs struct {
	srv       *httptest.Server
	downloads atomic.Int32
	failDL    atomic.Bool
}

func newFakeNodejs(t *testing.T) *fakeNodejs {
	t.Helper()
	f := &fakeNodejs{}
	mux := http.NewServeMux()
	mux.HandleFunc("/dist/index.json", func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write([]byte(nodeIndex)) })
	archive := func(w http.ResponseWriter, r *http.Request) {
		f.downloads.Add(1)
		if f.failDL.Load() {
			http.Error(w, "boom", http.StatusInternalServerError)
			return
		}
		version := strings.Split(r.URL.Path, "/")[len(strings.Split(r.URL.Path, "/"))-2]
		_, _ = w.Write(nodeTarball(t, version))
	}
	mux.HandleFunc("/dist/", archive)
	mux.HandleFunc("/unofficial/", archive)
	f.srv = httptest.NewServer(mux)
	t.Cleanup(f.srv.Close)
	return f
}

func (f *fakeNodejs) action() *SetupNodeAction {
	a := NewSetupNodeAction()
	a.distURL = f.srv.URL + "/dist"
	a.unofficialURL = f.srv.URL + "/unofficial"
	return a
}

func nodeTestEnv(t *testing.T) (*jobctx.JobContext, *runnerlog.Streamer, string) {
	t.Helper()
	if runtime.GOOS == "windows" {
		t.Skip("shell scripts stand in for node")
	}
	t.Setenv("RUNNER_TOOL_CACHE", t.TempDir())
	t.Setenv("PATH", "/usr/bin:/bin") // no node of the machine running the tests
	jctx := jobctx.New(nil, nil)
	streamer := runnerlog.New("step", nil, func(protocol.OutboundMessage) {}, zap.NewNop())
	return jctx, streamer, t.TempDir()
}

func TestSetupNode_downloadsCachesAndSetsPath(t *testing.T) {
	fake := newFakeNodejs(t)
	jctx, streamer, work := nodeTestEnv(t)
	a := fake.action()

	out, err := a.Execute(context.Background(), map[string]string{"node-version": "24"}, jctx, streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "v24.10.0", out["node-version"])
	assert.Equal(t, int32(1), fake.downloads.Load())
	assert.True(t, strings.HasPrefix(jctx.Env()["PATH"], filepath.Join(os.Getenv("RUNNER_TOOL_CACHE"), "node", "24.10.0")),
		"the installed bin directory comes first on PATH: %s", jctx.Env()["PATH"])
	assert.FileExists(t, filepath.Join(os.Getenv("RUNNER_TOOL_CACHE"), "node", "24.10.0", mustPlatform(t), "bin", "npm"))

	// Second time: found in the tool cache, nothing downloaded and the release list not needed.
	jctx2, _, _ := nodeTestEnv2(t)
	out, err = a.Execute(context.Background(), map[string]string{"node-version": "24.10"}, jctx2, streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "v24.10.0", out["node-version"])
	assert.Equal(t, int32(1), fake.downloads.Load(), "a cached version is not downloaded again")
}

// nodeTestEnv2 gives a fresh job but keeps the tool cache of the test.
func nodeTestEnv2(t *testing.T) (*jobctx.JobContext, *runnerlog.Streamer, string) {
	t.Helper()
	return jobctx.New(nil, nil), runnerlog.New("step", nil, func(protocol.OutboundMessage) {}, zap.NewNop()), t.TempDir()
}

func mustPlatform(t *testing.T) string {
	t.Helper()
	p, err := nodePlatform("")
	require.NoError(t, err)
	return p
}

func TestSetupNode_exactVersionSkipsTheReleaseList(t *testing.T) {
	fake := newFakeNodejs(t)
	jctx, streamer, work := nodeTestEnv(t)

	out, err := fake.action().Execute(context.Background(), map[string]string{"node-version": "20.11.1"}, jctx, streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "v20.11.1", out["node-version"])
}

func TestSetupNode_readsNodeVersionFile(t *testing.T) {
	fake := newFakeNodejs(t)
	jctx, streamer, work := nodeTestEnv(t)
	require.NoError(t, os.WriteFile(filepath.Join(work, ".nvmrc"), []byte("v22\n"), 0o600))

	out, err := fake.action().Execute(context.Background(), map[string]string{"node-version-file": ".nvmrc"}, jctx, streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "v22.9.0", out["node-version"])
}

func TestSetupNode_noVersionMeansLTS(t *testing.T) {
	fake := newFakeNodejs(t)
	jctx, streamer, work := nodeTestEnv(t)

	out, err := fake.action().Execute(context.Background(), map[string]string{}, jctx, streamer, work)
	require.NoError(t, err)
	assert.Equal(t, "v24.10.0", out["node-version"])
}

func TestSetupNode_failsClearlyWhenNothingMatches(t *testing.T) {
	fake := newFakeNodejs(t)
	jctx, streamer, work := nodeTestEnv(t)

	_, err := fake.action().Execute(context.Background(), map[string]string{"node-version": "18"}, jctx, streamer, work)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "no Node.js release matches")
}

func TestSetupNode_usesTheInstalledNodeWhenTheDownloadFails(t *testing.T) {
	fake := newFakeNodejs(t)
	fake.failDL.Store(true)
	jctx, streamer, work := nodeTestEnv(t)
	bin := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(bin, "node"), []byte("#!/bin/sh\necho v24.2.1\n"), 0o700)) //nolint:gosec
	t.Setenv("PATH", bin+":/usr/bin:/bin")

	out, err := fake.action().Execute(context.Background(), map[string]string{"node-version": "24"}, jctx, streamer, work)
	require.NoError(t, err, "a runner that already has a matching Node.js can still run the job")
	assert.Equal(t, "v24.2.1", out["node-version"])

	// ...but a different major is not silently accepted.
	_, err = fake.action().Execute(context.Background(), map[string]string{"node-version": "22"}, jctx, streamer, work)
	require.Error(t, err)
}

func TestExtractNodeArchive_rejectsPathsOutsideTheInstallDir(t *testing.T) {
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	tw := tar.NewWriter(gz)
	body := "x"
	require.NoError(t, tw.WriteHeader(&tar.Header{Name: "node-v1/../../evil", Typeflag: tar.TypeReg, Mode: 0o644, Size: int64(len(body))}))
	_, _ = tw.Write([]byte(body))
	require.NoError(t, tw.Close())
	require.NoError(t, gz.Close())

	dest := t.TempDir()
	err := extractNodeArchive(&buf, dest)
	require.Error(t, err)
	assert.NoFileExists(t, filepath.Join(filepath.Dir(dest), "evil"))
}
