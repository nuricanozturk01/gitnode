package actions

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/shell"
)

const (
	nodeDistURL       = "https://nodejs.org/dist"
	nodeUnofficialURL = "https://unofficial-builds.nodejs.org/download/release"
	nodeDownloadLimit = 10 * time.Minute
)

// SetupNodeAction implements actions/setup-node@v1 through v4.
//
// It downloads the requested Node.js from nodejs.org into the runner's tool cache and puts it on
// PATH for the rest of the job, so a runner does not need Node.js installed. On Alpine (musl) the
// unofficial musl build is used because the official binaries need glibc.
type SetupNodeAction struct {
	distURL       string
	unofficialURL string
	client        *http.Client
}

func NewSetupNodeAction() *SetupNodeAction {
	return &SetupNodeAction{
		distURL:       nodeDistURL,
		unofficialURL: nodeUnofficialURL,
		client:        &http.Client{Timeout: nodeDownloadLimit},
	}
}

func (a *SetupNodeAction) Name() string       { return "actions/setup-node" }
func (a *SetupNodeAction) Versions() []string { return []string{"v1", "v2", "v3", "v4"} }

func (a *SetupNodeAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	spec, err := nodeVersionSpec(inputs, workDir)
	if err != nil {
		return nil, err
	}
	streamer.Emit(fmt.Sprintf("Setting up Node.js %s", spec), "info")

	binDir, version, installErr := a.install(ctx, spec, inputs["architecture"], streamer)
	if installErr != nil {
		// A runner that already has a matching Node.js can still run the job.
		if installed := installedNodeVersion(ctx, jctx); installed != "" && nodeMatches(spec, installed) {
			streamer.Emit(
				fmt.Sprintf("Could not download Node.js (%v); using %s already on PATH", installErr, installed),
				"warning")
			return map[string]string{"node-version": installed}, nil
		}
		return nil, fmt.Errorf("setup-node: failed to install Node.js %s: %w", spec, installErr)
	}

	prependPath(jctx, binDir)

	if conclusion, runErr := shell.Run(ctx, "node --version && npm --version", workDir, jctx, streamer); runErr != nil ||
		conclusion == "failure" {
		return nil, fmt.Errorf("setup-node: Node.js %s was installed but does not run", version)
	}

	if cacheBackend := inputs["cache"]; cacheBackend != "" {
		streamer.Emit(fmt.Sprintf("Cache backend: %s (handled by actions/cache)", cacheBackend), "info")
	}
	streamer.Emit(fmt.Sprintf("Node.js %s ready", version), "info")
	return map[string]string{"node-version": version}, nil
}

// nodeVersionSpec is the requested version: the input, else the file named by node-version-file,
// else the current LTS.
func nodeVersionSpec(inputs map[string]string, workDir string) (string, error) {
	if v := strings.TrimSpace(inputs["node-version"]); v != "" {
		return v, nil
	}
	if file := strings.TrimSpace(inputs["node-version-file"]); file != "" {
		path := file
		if !filepath.IsAbs(path) {
			path = filepath.Join(workDir, file)
		}
		data, err := os.ReadFile(path) //nolint:gosec // path comes from the workflow's own checkout
		if err != nil {
			return "", fmt.Errorf("setup-node: cannot read node-version-file %q: %w", file, err)
		}
		if v := strings.TrimSpace(string(data)); v != "" {
			return v, nil
		}
		return "", fmt.Errorf("setup-node: node-version-file %q is empty", file)
	}
	return "lts", nil
}

// install makes sure the version is in the tool cache and returns its bin directory and exact version.
func (a *SetupNodeAction) install(
	ctx context.Context,
	spec, arch string,
	streamer *runnerlog.Streamer,
) (string, string, error) {

	platform, err := nodePlatform(arch)
	if err != nil {
		return "", "", err
	}
	// Like GitHub's runner: a matching version already in the tool cache is used without asking
	// nodejs.org; only a missing one is resolved and downloaded.
	version := cachedNodeVersion(spec, platform)
	if version == "" {
		version, err = a.resolveVersion(ctx, spec)
		if err != nil {
			return "", "", err
		}
	}

	dir := filepath.Join(toolCache(), "node", strings.TrimPrefix(version, "v"), platform)
	binDir := filepath.Join(dir, "bin")
	if _, statErr := os.Stat(filepath.Join(binDir, "node")); statErr == nil {
		streamer.Emit(fmt.Sprintf("Node.js %s found in the tool cache", version), "info")
		return binDir, version, nil
	}

	url := a.archiveURL(version, platform)
	streamer.Emit(fmt.Sprintf("Downloading %s", url), "info")
	if err := a.downloadAndExtract(ctx, url, dir); err != nil {
		return "", "", err
	}
	return binDir, version, nil
}

// cachedNodeVersion is the newest version in the tool cache that satisfies a numeric spec (24, 24.2,
// 24.2.1), or "" when none does. Specs such as lts or latest always need the release list.
func cachedNodeVersion(spec, platform string) string {
	spec = normalizeNodeSpec(spec)
	entries, err := os.ReadDir(filepath.Join(toolCache(), "node"))
	if err != nil {
		return ""
	}
	best := ""
	for _, e := range entries {
		if !e.IsDir() || !nodeMatches(spec, e.Name()) {
			continue
		}
		if _, statErr := os.Stat(filepath.Join(toolCache(), "node", e.Name(), platform, "bin", "node")); statErr != nil {
			continue
		}
		if best == "" || compareVersions(e.Name(), best) > 0 {
			best = e.Name()
		}
	}
	if best == "" {
		return ""
	}
	return "v" + best
}

// compareVersions compares dotted numeric versions (24.2.1 against 24.10.0): positive when a is newer.
func compareVersions(a, b string) int {
	as, bs := strings.Split(a, "."), strings.Split(b, ".")
	for i := 0; i < len(as) || i < len(bs); i++ {
		x, y := versionPart(as, i), versionPart(bs, i)
		if x != y {
			return x - y
		}
	}
	return 0
}

func versionPart(parts []string, i int) int {
	if i >= len(parts) {
		return 0
	}
	n := 0
	for _, c := range parts[i] {
		if c < '0' || c > '9' {
			break
		}
		n = n*10 + int(c-'0')
	}
	return n
}

// nodeRelease is one entry of nodejs.org/dist/index.json; lts is false or the codename.
type nodeRelease struct {
	Version string `json:"version"`
	LTS     any    `json:"lts"`
}

func (r nodeRelease) isLTS() bool {
	name, ok := r.LTS.(string)
	return ok && name != ""
}

// resolveVersion turns "24", "24.1", "20.11.1", "lts", "lts/iron" or "latest" into a version like v24.2.0.
func (a *SetupNodeAction) resolveVersion(ctx context.Context, spec string) (string, error) {
	spec = normalizeNodeSpec(spec)
	if isExactNodeVersion(spec) {
		return "v" + spec, nil
	}

	releases, err := a.fetchIndex(ctx)
	if err != nil {
		return "", err
	}
	if v := pickNodeRelease(releases, spec); v != "" {
		return v, nil
	}
	return "", fmt.Errorf("no Node.js release matches %q", spec)
}

func normalizeNodeSpec(spec string) string {
	spec = strings.ToLower(strings.TrimSpace(spec))
	spec = strings.TrimLeft(spec, "^~=v ")
	return spec
}

func isExactNodeVersion(spec string) bool {
	parts := strings.Split(spec, ".")
	if len(parts) != 3 {
		return false
	}
	for _, p := range parts {
		if p == "" || strings.Trim(p, "0123456789") != "" {
			return false
		}
	}
	return true
}

// pickNodeRelease chooses from releases (newest first) the one spec asks for; "" when none.
func pickNodeRelease(releases []nodeRelease, spec string) string {
	for _, r := range releases {
		if strings.Contains(r.Version, "-") {
			continue // pre-release
		}
		if nodeReleaseMatches(r, spec) {
			return r.Version
		}
	}
	return ""
}

func nodeReleaseMatches(r nodeRelease, spec string) bool {
	switch {
	case spec == "latest" || spec == "current" || spec == "node" || spec == "*" || spec == "":
		return true
	case spec == "lts" || spec == "lts/*":
		return r.isLTS()
	case strings.HasPrefix(spec, "lts/"):
		name, _ := r.LTS.(string)
		return strings.EqualFold(name, strings.TrimPrefix(spec, "lts/"))
	}
	return nodeMatches(spec, r.Version)
}

// nodeMatches reports whether version (v24.2.0 or 24.2.0) satisfies a numeric spec such as 24, 24.2 or 24.x.
func nodeMatches(spec, version string) bool {
	spec = strings.TrimSuffix(strings.TrimSuffix(normalizeNodeSpec(spec), ".x"), ".x")
	version = strings.TrimPrefix(strings.ToLower(version), "v")
	if spec == "" || strings.Trim(spec, "0123456789.") != "" {
		return false
	}
	return version == spec || strings.HasPrefix(version, spec+".")
}

func (a *SetupNodeAction) fetchIndex(ctx context.Context) ([]nodeRelease, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, a.distURL+"/index.json", nil)
	if err != nil {
		return nil, err
	}
	resp, err := a.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch Node.js release list: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("fetch Node.js release list: %s", resp.Status)
	}
	var releases []nodeRelease
	if err := json.NewDecoder(resp.Body).Decode(&releases); err != nil {
		return nil, fmt.Errorf("read Node.js release list: %w", err)
	}
	return releases, nil
}

// nodePlatform is the "linux-x64" part of a Node.js archive name, with "-musl" on Alpine.
func nodePlatform(arch string) (string, error) {
	if arch == "" {
		switch runtime.GOARCH {
		case "amd64":
			arch = "x64"
		case "386":
			arch = "x86"
		case "arm":
			arch = "armv7l"
		default:
			arch = runtime.GOARCH // arm64, ppc64le, s390x
		}
	}
	switch runtime.GOOS {
	case "linux":
		if isMusl() {
			return "linux-" + arch + "-musl", nil
		}
		return "linux-" + arch, nil
	case "darwin":
		return "darwin-" + arch, nil
	}
	return "", fmt.Errorf("downloading Node.js is not supported on %s; install Node.js on the runner", runtime.GOOS)
}

func isMusl() bool {
	if _, err := os.Stat("/etc/alpine-release"); err == nil {
		return true
	}
	matches, _ := filepath.Glob("/lib/ld-musl-*")
	return len(matches) > 0
}

// archiveURL is where a platform's tarball lives; musl builds are only published unofficially.
func (a *SetupNodeAction) archiveURL(version, platform string) string {
	name := fmt.Sprintf("node-%s-%s.tar.gz", version, platform)
	if strings.HasSuffix(platform, "-musl") {
		return fmt.Sprintf("%s/%s/%s", a.unofficialURL, version, name)
	}
	return fmt.Sprintf("%s/%s/%s", a.distURL, version, name)
}

func (a *SetupNodeAction) downloadAndExtract(ctx context.Context, url, dest string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	resp, err := a.client.Do(req)
	if err != nil {
		return fmt.Errorf("download %s: %w", url, err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("download %s: %s", url, resp.Status)
	}

	// Extract next to the target and rename, so a half-written install is never used.
	if err = os.MkdirAll(filepath.Dir(dest), 0o750); err != nil {
		return err
	}
	tmp, err := os.MkdirTemp(filepath.Dir(dest), ".node-install-*")
	if err != nil {
		return err
	}
	defer func() { _ = os.RemoveAll(tmp) }()

	if err = extractNodeArchive(resp.Body, tmp); err != nil {
		return fmt.Errorf("unpack %s: %w", url, err)
	}
	if err = os.RemoveAll(dest); err != nil {
		return err
	}
	return os.Rename(tmp, dest)
}

// extractNodeArchive unpacks a gzipped tar into dest, dropping the archive's top-level directory.
func extractNodeArchive(r io.Reader, dest string) error {
	gz, err := gzip.NewReader(r)
	if err != nil {
		return err
	}
	defer func() { _ = gz.Close() }()

	tr := tar.NewReader(gz)
	for {
		hdr, err := tr.Next()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}
		if err := extractTarEntry(tr, hdr, dest); err != nil {
			return err
		}
	}
}

func extractTarEntry(tr *tar.Reader, hdr *tar.Header, dest string) error {
	rel := stripFirstDir(hdr.Name)
	if rel == "" {
		return nil
	}
	target := filepath.Join(dest, rel)
	if !strings.HasPrefix(target, filepath.Clean(dest)+string(os.PathSeparator)) {
		return fmt.Errorf("archive entry %q leaves the install directory", hdr.Name)
	}

	switch hdr.Typeflag {
	case tar.TypeDir:
		return os.MkdirAll(target, 0o750) //nolint:gosec // mode is set below by the files inside
	case tar.TypeReg:
		return writeTarFile(tr, target, hdr.FileInfo().Mode().Perm())
	case tar.TypeSymlink:
		if err := os.MkdirAll(filepath.Dir(target), 0o750); err != nil {
			return err
		}
		return os.Symlink(hdr.Linkname, target)
	}
	return nil
}

func writeTarFile(r io.Reader, target string, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(target), 0o750); err != nil {
		return err
	}
	f, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode) //nolint:gosec // inside the install dir
	if err != nil {
		return err
	}
	if _, err := io.Copy(f, r); err != nil { //nolint:gosec // the archive is the Node.js release
		_ = f.Close()
		return err
	}
	return f.Close()
}

func stripFirstDir(name string) string {
	name = strings.TrimPrefix(filepath.ToSlash(name), "./")
	_, rest, found := strings.Cut(name, "/")
	if !found {
		return ""
	}
	return filepath.FromSlash(strings.TrimSuffix(rest, "/"))
}

// toolCache is where downloaded tools are kept between jobs (the runner sets RUNNER_TOOL_CACHE).
func toolCache() string {
	dir := os.Getenv("RUNNER_TOOL_CACHE")
	if dir == "" {
		dir = filepath.Join(os.TempDir(), "gitnode-runner-toolcache")
	}
	return dir
}

// prependPath puts dir first on the PATH later steps of the job see.
func prependPath(jctx *jobctx.JobContext, dir string) {
	current, ok := jctx.Env()["PATH"]
	if !ok {
		current = os.Getenv("PATH")
	}
	jctx.SetEnv("PATH", dir+string(os.PathListSeparator)+current)
}

// installedNodeVersion is the version of the node on PATH, or "" when there is none.
func installedNodeVersion(ctx context.Context, jctx *jobctx.JobContext) string {
	cmd := exec.CommandContext(ctx, "node", "--version") //nolint:gosec // fixed command
	cmd.Env = os.Environ()
	for k, v := range jctx.Env() {
		cmd.Env = append(cmd.Env, k+"="+v)
	}
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}
