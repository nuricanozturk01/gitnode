package executor

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// runtimeFiles are the per-job files GitHub-style workflows write to: $GITHUB_OUTPUT,
// $GITHUB_ENV, $GITHUB_PATH and $GITHUB_STEP_SUMMARY.
type runtimeFiles struct {
	dir     string
	output  string
	env     string
	path    string
	summary string
}

func newRuntimeFiles() (*runtimeFiles, error) {
	dir, err := os.MkdirTemp("", "gitnode-runtime-")
	if err != nil {
		return nil, fmt.Errorf("create runtime dir: %w", err)
	}
	r := &runtimeFiles{
		dir:     dir,
		output:  filepath.Join(dir, "github_output"),
		env:     filepath.Join(dir, "github_env"),
		path:    filepath.Join(dir, "github_path"),
		summary: filepath.Join(dir, "github_step_summary"),
	}
	for _, f := range []string{r.output, r.env, r.path, r.summary} {
		if err := os.WriteFile(f, nil, 0o600); err != nil {
			_ = os.RemoveAll(dir)
			return nil, fmt.Errorf("create %s: %w", filepath.Base(f), err)
		}
	}
	return r, nil
}

func (r *runtimeFiles) cleanup() { _ = os.RemoveAll(r.dir) }

// drain returns the content of path and truncates it so the next step starts clean.
func drain(path string) string {
	b, err := os.ReadFile(path) //nolint:gosec
	if err != nil || len(b) == 0 {
		return ""
	}
	_ = os.WriteFile(path, nil, 0o600)
	return string(b)
}

// parseKeyValues parses the $GITHUB_OUTPUT / $GITHUB_ENV format:
//
//	name=value
//	name<<DELIMITER
//	multi-line
//	value
//	DELIMITER
//
// Malformed lines are ignored. Later assignments win.
func parseKeyValues(data string) map[string]string {
	out := map[string]string{}
	lines := strings.Split(strings.ReplaceAll(data, "\r\n", "\n"), "\n")
	for i := 0; i < len(lines); i++ {
		line := lines[i]
		if strings.TrimSpace(line) == "" {
			continue
		}
		eq := strings.Index(line, "=")
		heredoc := strings.Index(line, "<<")
		if heredoc > 0 && (eq < 0 || heredoc < eq) {
			name := line[:heredoc]
			delim := line[heredoc+2:]
			if name == "" || delim == "" {
				continue
			}
			var val []string
			closed := false
			for i++; i < len(lines); i++ {
				if lines[i] == delim {
					closed = true
					break
				}
				val = append(val, lines[i])
			}
			if closed {
				out[name] = strings.Join(val, "\n")
			}
			continue
		}
		if eq > 0 {
			out[line[:eq]] = line[eq+1:]
		}
	}
	return out
}

// parsePathEntries returns the non-empty lines of a $GITHUB_PATH file.
func parsePathEntries(data string) []string {
	var entries []string
	for _, l := range strings.Split(strings.ReplaceAll(data, "\r\n", "\n"), "\n") {
		if l = strings.TrimSpace(l); l != "" {
			entries = append(entries, l)
		}
	}
	return entries
}
