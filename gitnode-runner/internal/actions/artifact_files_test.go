package actions

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"os"
	"path/filepath"
	"sort"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func writeTree(t *testing.T, root string, files map[string]string) {
	t.Helper()
	for name, content := range files {
		path := filepath.Join(root, filepath.FromSlash(name))
		require.NoError(t, os.MkdirAll(filepath.Dir(path), 0o750))
		require.NoError(t, os.WriteFile(path, []byte(content), 0o644)) //nolint:gosec
	}
}

func names(files []artifactFile) []string {
	out := make([]string, len(files))
	for i, f := range files {
		out[i] = f.rel
	}
	sort.Strings(out)
	return out
}

func TestCollectArtifactFiles(t *testing.T) {
	root := t.TempDir()
	writeTree(t, root, map[string]string{
		"out.txt":          "1",
		"dist/a.js":        "2",
		"dist/a.js.map":    "3",
		"dist/sub/b.js":    "4",
		"dist/.hidden":     "5",
		"docs/readme.md":   "6",
		"logs/run/1.log":   "7",
		"logs/run/2.log":   "8",
		"other/ignored.go": "9",
	})

	cases := []struct {
		name          string
		path          string
		includeHidden bool
		want          []string
	}{
		{"single file", "out.txt", false, []string{"out.txt"}},
		{"directory is its contents", "dist", false, []string{"a.js", "a.js.map", "sub/b.js"}},
		{"directory with hidden", "dist", true, []string{".hidden", "a.js", "a.js.map", "sub/b.js"}},
		{"recursive glob", "dist/**/*.js", false, []string{"a.js", "sub/b.js"}},
		{"star does not cross directories", "dist/*.js", false, []string{"a.js"}},
		{"negation", "dist/**\n!dist/**/*.map", false, []string{"a.js", "sub/b.js"}},
		{"several paths keep their common root", "dist/a.js\ndocs/readme.md", false,
			[]string{"dist/a.js", "docs/readme.md"}},
		{"glob selecting a directory", "logs/*", false, []string{"run/1.log", "run/2.log"}},
		{"question mark", "logs/run/?.log", false, []string{"1.log", "2.log"}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, err := collectArtifactFiles(root, c.path, c.includeHidden)
			require.NoError(t, err)
			assert.Equal(t, c.want, names(got))
		})
	}
}

func TestCollectArtifactFilesErrors(t *testing.T) {
	root := t.TempDir()
	writeTree(t, root, map[string]string{"a.txt": "x"})
	_, err := collectArtifactFiles(root, "missing.txt", false)
	assert.Error(t, err)
	_, err = collectArtifactFiles(root, "   \n", false)
	assert.Error(t, err)
	_, err = collectArtifactFiles(root, "*.nothing", false)
	assert.Error(t, err)
}

func TestArtifactRoundTripKeepsContentAndMode(t *testing.T) {
	root := t.TempDir()
	writeTree(t, root, map[string]string{"bin/tool": "#!/bin/sh\necho hi\n", "bin/data/x.txt": "x"})
	require.NoError(t, os.Chmod(filepath.Join(root, "bin", "tool"), 0o755)) //nolint:gosec

	files, err := collectArtifactFiles(root, "bin", false)
	require.NoError(t, err)
	var buf bytes.Buffer
	require.NoError(t, tarGzFiles(files, &buf))

	dest := t.TempDir()
	require.NoError(t, unTarGz(&buf, dest))
	got, err := os.ReadFile(filepath.Join(dest, "data", "x.txt"))
	require.NoError(t, err)
	assert.Equal(t, "x", string(got))
	info, err := os.Stat(filepath.Join(dest, "tool"))
	require.NoError(t, err)
	assert.NotZero(t, info.Mode().Perm()&0o100, "executable bit must survive")
}

func TestUnTarGzRefusesPathTraversal(t *testing.T) {
	var raw bytes.Buffer
	gw := gzip.NewWriter(&raw)
	tw := tar.NewWriter(gw)
	require.NoError(t, tw.WriteHeader(&tar.Header{Name: "../evil.txt", Mode: 0o644, Size: 1, Typeflag: tar.TypeReg}))
	_, err := tw.Write([]byte("x"))
	require.NoError(t, err)
	require.NoError(t, tw.Close())
	require.NoError(t, gw.Close())

	dest := filepath.Join(t.TempDir(), "dest")
	require.NoError(t, os.MkdirAll(dest, 0o750))
	assert.Error(t, unTarGz(&raw, dest))
	_, statErr := os.Stat(filepath.Join(filepath.Dir(dest), "evil.txt"))
	assert.True(t, os.IsNotExist(statErr), "nothing may be written outside the destination")
}
