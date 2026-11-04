package actions

import (
	"archive/tar"
	"compress/gzip"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

// artifactFile is one file picked by an upload-artifact `path:`.
type artifactFile struct {
	abs  string // location on disk
	rel  string // name inside the artifact, relative to the least common ancestor
	mode fs.FileMode
	size int64
}

// collectArtifactFiles resolves an upload-artifact `path:` the way GitHub does: one pattern per
// line, relative to the workspace, `*`, `**`, `?` and `[...]` globs, a leading `!` excludes, a
// directory stands for everything below it. Files are named relative to the least common ancestor
// of the search paths, and hidden files are left out unless includeHidden is set.
func collectArtifactFiles(workDir, pathInput string, includeHidden bool) ([]artifactFile, error) {
	picked, bases, err := expandPatterns(workDir, pathInput)
	if err != nil {
		return nil, err
	}
	root := commonAncestor(bases)

	var out []artifactFile
	for _, file := range picked {
		rel := strings.TrimPrefix(strings.TrimPrefix(file, root), "/")
		if !includeHidden && hasHiddenPart(rel) {
			continue
		}
		info, err := os.Stat(filepath.FromSlash(file))
		if err != nil || !info.Mode().IsRegular() {
			continue
		}
		out = append(out, artifactFile{abs: filepath.FromSlash(file), rel: rel, mode: info.Mode().Perm(), size: info.Size()})
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("no files found")
	}
	sort.Slice(out, func(i, j int) bool { return out[i].rel < out[j].rel })
	return out, nil
}

// searchPath is one include pattern: its static directory prefix and the regexp for the rest.
type searchPath struct {
	pattern string // absolute, slash separated
	base    string // directory the walk starts in (the static prefix)
	re      *regexp.Regexp
	literal bool // no wildcard: the pattern names a file or directory exactly
}

func newSearchPath(abs string) (searchPath, error) {
	sp := searchPath{pattern: abs}
	idx := strings.IndexAny(abs, "*?[")
	if idx < 0 {
		sp.literal = true
		if info, err := os.Stat(filepath.FromSlash(abs)); err == nil && info.IsDir() {
			sp.base = abs
		} else {
			sp.base = filepath.ToSlash(filepath.Dir(filepath.FromSlash(abs)))
		}
		return sp, nil
	}
	sp.base = abs[:strings.LastIndex(abs[:idx], "/")+1]
	sp.base = strings.TrimSuffix(sp.base, "/")
	if sp.base == "" {
		sp.base = "/"
	}
	re, err := globToRegexp(abs)
	if err != nil {
		return sp, err
	}
	sp.re = re
	return sp, nil
}

// files lists the regular files this pattern selects.
func (sp searchPath) files() []string {
	var out []string
	if sp.literal {
		info, err := os.Stat(filepath.FromSlash(sp.pattern))
		switch {
		case err != nil:
		case info.IsDir():
			out = walkFiles(sp.pattern)
		case info.Mode().IsRegular():
			out = append(out, sp.pattern)
		}
		return out
	}
	for _, file := range walkFiles(sp.base) {
		if matchesWithAncestors(sp.re, file, sp.base) {
			out = append(out, file)
		}
	}
	return out
}

func walkFiles(dir string) []string {
	var out []string
	_ = filepath.WalkDir(filepath.FromSlash(dir), func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil //nolint:nilerr // unreadable entries are skipped
		}
		out = append(out, filepath.ToSlash(path))
		return nil
	})
	return out
}

// matchesWithAncestors is true when the file, or a directory above it (below base), matches: a
// pattern that selects a directory selects everything in it.
func matchesWithAncestors(re *regexp.Regexp, file, base string) bool {
	for p := file; len(p) > len(base); p = p[:strings.LastIndex(p, "/")] {
		if re.MatchString(p) {
			return true
		}
	}
	return false
}

func excluded(file string, excludes []*regexp.Regexp) bool {
	for _, re := range excludes {
		for p := file; p != "" && strings.Contains(p, "/"); p = p[:strings.LastIndex(p, "/")] {
			if re.MatchString(p) {
				return true
			}
		}
	}
	return false
}

// globToRegexp translates a GitHub glob into an anchored regexp over slash separated paths.
func globToRegexp(glob string) (*regexp.Regexp, error) {
	var b strings.Builder
	b.WriteString("^")
	for i := 0; i < len(glob); i++ {
		switch c := glob[i]; c {
		case '*':
			if i+1 < len(glob) && glob[i+1] == '*' {
				i++
				if i+1 < len(glob) && glob[i+1] == '/' {
					i++
					b.WriteString("(?:.*/)?")
				} else {
					b.WriteString(".*")
				}
			} else {
				b.WriteString("[^/]*")
			}
		case '?':
			b.WriteString("[^/]")
		case '[':
			end := strings.IndexByte(glob[i:], ']')
			if end < 0 {
				b.WriteString(`\[`)
				continue
			}
			class := glob[i+1 : i+end]
			class = strings.TrimPrefix(strings.ReplaceAll(class, `\`, `\\`), "!")
			if strings.HasPrefix(glob[i+1:], "!") {
				b.WriteString("[^" + class + "]")
			} else {
				b.WriteString("[" + class + "]")
			}
			i += end
		default:
			b.WriteString(regexp.QuoteMeta(string(c)))
		}
	}
	b.WriteString("$")
	re, err := regexp.Compile(b.String())
	if err != nil {
		return nil, fmt.Errorf("invalid path pattern %q: %w", glob, err)
	}
	return re, nil
}

func commonAncestor(dirs []string) string {
	if len(dirs) == 0 {
		return ""
	}
	parts := strings.Split(dirs[0], "/")
	for _, d := range dirs[1:] {
		other := strings.Split(d, "/")
		n := 0
		for n < len(parts) && n < len(other) && parts[n] == other[n] {
			n++
		}
		parts = parts[:n]
	}
	return strings.Join(parts, "/")
}

func hasHiddenPart(rel string) bool {
	for _, part := range strings.Split(rel, "/") {
		if strings.HasPrefix(part, ".") && part != "." && part != ".." {
			return true
		}
	}
	return false
}

// tarGzFiles writes the picked files as a tar.gz with their artifact names.
func tarGzFiles(files []artifactFile, w io.Writer) error {
	gw := gzip.NewWriter(w)
	tw := tar.NewWriter(gw)
	for _, f := range files {
		hdr := &tar.Header{Name: f.rel, Mode: int64(f.mode), Size: f.size, Typeflag: tar.TypeReg}
		if err := tw.WriteHeader(hdr); err != nil {
			return err
		}
		in, err := os.Open(f.abs)
		if err != nil {
			return err
		}
		_, copyErr := io.Copy(tw, in)
		closeErr := in.Close()
		if copyErr != nil {
			return copyErr
		}
		if closeErr != nil {
			return closeErr
		}
	}
	if err := tw.Close(); err != nil {
		return err
	}
	return gw.Close()
}

// expandPatterns resolves a multi-line path input (artifact or cache) to the files it selects and
// to the directories the patterns start in. See collectArtifactFiles for the pattern rules.
// A leading "~/" stands for the home directory of the runner's user.
func expandPatterns(workDir, pathInput string) (files, bases []string, err error) {
	var includes []searchPath
	var excludes []*regexp.Regexp
	for _, line := range strings.Split(pathInput, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		negate := strings.HasPrefix(line, "!")
		abs := absolutePattern(workDir, strings.TrimPrefix(line, "!"))
		if negate {
			re, reErr := globToRegexp(abs)
			if reErr != nil {
				return nil, nil, reErr
			}
			excludes = append(excludes, re)
			continue
		}
		sp, spErr := newSearchPath(abs)
		if spErr != nil {
			return nil, nil, spErr
		}
		includes = append(includes, sp)
	}
	if len(includes) == 0 {
		return nil, nil, fmt.Errorf("no path given")
	}

	seen := map[string]bool{}
	for _, sp := range includes {
		bases = append(bases, sp.base)
		for _, file := range sp.files() {
			if seen[file] || excluded(file, excludes) {
				continue
			}
			seen[file] = true
			files = append(files, file)
		}
	}
	if len(files) == 0 {
		return nil, nil, fmt.Errorf("no files found")
	}
	return files, bases, nil
}

// absolutePattern turns a pattern into an absolute, slash separated path.
func absolutePattern(workDir, pattern string) string {
	if pattern == "~" || strings.HasPrefix(pattern, "~/") {
		if home, err := os.UserHomeDir(); err == nil {
			pattern = filepath.Join(home, strings.TrimPrefix(pattern, "~"))
		}
	}
	if !filepath.IsAbs(pattern) {
		pattern = filepath.Join(workDir, pattern)
	}
	return filepath.ToSlash(filepath.Clean(pattern))
}
