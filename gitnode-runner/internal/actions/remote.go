package actions

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/plumbing"
	"gopkg.in/yaml.v3"
)

// Action kinds, from the `runs.using` field of an action manifest.
const (
	KindComposite = "composite"
	KindNode      = "node"
	KindDocker    = "docker"
)

// maxActionRefLen guards against absurd refs ending up in cache paths.
const maxActionRefLen = 200

var (
	shaRe       = regexp.MustCompile(`^[0-9a-fA-F]{40}$`)
	ownerRepoRe = regexp.MustCompile(`^[A-Za-z0-9_.-]+$`)
)

// Manifest is the parsed action.yml / action.yaml of an action.
type Manifest struct {
	Name        string
	Description string
	Inputs      map[string]ManifestInput
	// Outputs maps output name to its value expression (composite actions).
	Outputs map[string]string
	Runs    Runs
}

// ManifestInput describes one declared input.
type ManifestInput struct {
	Description string
	Required    bool
	Default     string
	HasDefault  bool
}

// Runs is the `runs:` block.
type Runs struct {
	Using string
	// node actions
	Main   string
	Pre    string
	PreIf  string
	Post   string
	PostIf string
	// docker actions
	Image      string
	Args       []string
	Entrypoint string
	Env        map[string]string
	// composite actions
	Steps []CompositeStep
}

// CompositeStep is one step of a composite action.
type CompositeStep struct {
	ID               string
	Name             string
	If               string
	Run              string
	Shell            string
	Uses             string
	With             map[string]string
	Env              map[string]string
	WorkingDirectory string
	ContinueOnError  bool
}

// Kind normalises runs.using: node12/node16/node20/node24 → KindNode.
func (r Runs) Kind() string {
	u := strings.ToLower(strings.TrimSpace(r.Using))
	switch {
	case u == KindComposite:
		return KindComposite
	case u == KindDocker:
		return KindDocker
	case strings.HasPrefix(u, "node"):
		return KindNode
	}
	return ""
}

// LoadedAction is an action fetched to disk with its manifest.
type LoadedAction struct {
	// Dir is the directory containing the manifest (the action's root or sub-path).
	Dir      string
	Manifest Manifest
}

// Ref is a parsed `uses:` reference to an action that is not built in.
type Ref struct {
	Owner string
	Repo  string
	// Path is a sub-directory inside the repository ("" for the root).
	Path  string
	Value string // the version: tag, branch or commit
	// Local is set for `./relative/path` actions that live in the checked-out workspace.
	Local string
}

// ParseRef parses `owner/repo[/path]@ref` and `./local/path` references.
func ParseRef(uses string) (Ref, error) {
	uses = strings.TrimSpace(uses)
	if strings.HasPrefix(uses, "./") || strings.HasPrefix(uses, "../") {
		return Ref{Local: uses}, nil
	}
	if strings.HasPrefix(uses, "docker://") {
		return Ref{}, fmt.Errorf("docker:// references are not supported as steps: %q", uses)
	}
	at := strings.LastIndex(uses, "@")
	if at <= 0 || at == len(uses)-1 {
		return Ref{}, fmt.Errorf("invalid action reference %q (expected owner/repo@version)", uses)
	}
	nameRepo, version := uses[:at], uses[at+1:]
	if len(version) > maxActionRefLen || strings.Contains(version, "..") {
		return Ref{}, fmt.Errorf("invalid version in %q", uses)
	}
	parts := strings.Split(nameRepo, "/")
	if len(parts) < 2 || !ownerRepoRe.MatchString(parts[0]) || !ownerRepoRe.MatchString(parts[1]) ||
		parts[0] == "." || parts[0] == ".." || parts[1] == "." || parts[1] == ".." {
		return Ref{}, fmt.Errorf("invalid action reference %q (expected owner/repo@version)", uses)
	}
	sub := strings.Join(parts[2:], "/")
	if strings.Contains(sub, "..") {
		return Ref{}, fmt.Errorf("invalid action path in %q", uses)
	}
	return Ref{Owner: parts[0], Repo: parts[1], Path: sub, Value: version}, nil
}

// Resolver downloads actions and reads their manifests.
type Resolver struct {
	cacheDir string
	// repoURL returns the clone URL for owner/repo. Defaults to github.com.
	repoURL func(owner, repo string) string
	// ttl controls how long a clone of a moving ref (branch or tag) is reused.
	ttl time.Duration

	mu sync.Mutex
}

// NewResolver returns a Resolver caching clones below cacheDir.
func NewResolver(cacheDir string) *Resolver {
	return &Resolver{
		cacheDir: cacheDir,
		repoURL:  func(owner, repo string) string { return "https://github.com/" + owner + "/" + repo + ".git" },
		ttl:      time.Hour,
	}
}

// NewResolverWithURL is NewResolver with a custom clone-URL function (mirrors, tests).
func NewResolverWithURL(cacheDir string, repoURL func(owner, repo string) string) *Resolver {
	r := NewResolver(cacheDir)
	r.repoURL = repoURL
	return r
}

// Load fetches (or reuses) the action and parses its manifest. workDir resolves local
// `./path` references.
func (r *Resolver) Load(ctx context.Context, uses, workDir string) (*LoadedAction, error) {
	ref, err := ParseRef(uses)
	if err != nil {
		return nil, err
	}
	var dir string
	if ref.Local != "" {
		dir = filepath.Join(workDir, filepath.FromSlash(ref.Local))
		rel, relErr := filepath.Rel(workDir, filepath.Clean(dir))
		if relErr != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
			return nil, fmt.Errorf("local action %q is outside the workspace", uses)
		}
	} else {
		root, fetchErr := r.fetch(ctx, ref)
		if fetchErr != nil {
			return nil, fetchErr
		}
		dir = filepath.Join(root, filepath.FromSlash(ref.Path))
	}
	m, err := readManifest(dir)
	if err != nil {
		return nil, fmt.Errorf("action %q: %w", uses, err)
	}
	return &LoadedAction{Dir: dir, Manifest: m}, nil
}

func (r *Resolver) fetch(ctx context.Context, ref Ref) (string, error) {
	sum := sha256.Sum256([]byte(ref.Owner + "/" + ref.Repo + "@" + ref.Value))
	dest := filepath.Join(r.cacheDir, ref.Owner+"-"+ref.Repo+"-"+hex.EncodeToString(sum[:6]))
	marker := filepath.Join(dest, ".gitnode-fetched")

	r.mu.Lock()
	defer r.mu.Unlock()

	if st, err := os.Stat(marker); err == nil {
		if shaRe.MatchString(ref.Value) || time.Since(st.ModTime()) < r.ttl {
			return dest, nil
		}
	}
	if err := os.RemoveAll(dest); err != nil {
		return "", fmt.Errorf("clean action cache: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(dest), 0o750); err != nil {
		return "", fmt.Errorf("create action cache: %w", err)
	}
	if err := cloneAt(ctx, r.repoURL(ref.Owner, ref.Repo), dest, ref.Value); err != nil {
		_ = os.RemoveAll(dest)
		return "", fmt.Errorf("fetch action %s/%s@%s: %w", ref.Owner, ref.Repo, ref.Value, err)
	}
	if err := os.WriteFile(marker, []byte(time.Now().UTC().Format(time.RFC3339)), 0o600); err != nil {
		return "", fmt.Errorf("write cache marker: %w", err)
	}
	return dest, nil
}

// cloneAt clones url into dest at version, which may be a tag, a branch or a commit SHA.
func cloneAt(ctx context.Context, url, dest, version string) error {
	if shaRe.MatchString(version) {
		repo, err := git.PlainCloneContext(ctx, dest, false, &git.CloneOptions{URL: url})
		if err != nil {
			return err
		}
		wt, err := repo.Worktree()
		if err != nil {
			return err
		}
		return wt.Checkout(&git.CheckoutOptions{Hash: plumbing.NewHash(version), Force: true})
	}
	var lastErr error
	for _, name := range []plumbing.ReferenceName{
		plumbing.NewTagReferenceName(version),
		plumbing.NewBranchReferenceName(version),
	} {
		_ = os.RemoveAll(dest)
		_, err := git.PlainCloneContext(ctx, dest, false, &git.CloneOptions{
			URL: url, ReferenceName: name, SingleBranch: true, Depth: 1,
		})
		if err == nil {
			return nil
		}
		lastErr = err
		if ctx.Err() != nil {
			return ctx.Err()
		}
	}
	if lastErr == nil {
		lastErr = errors.New("ref not found")
	}
	return fmt.Errorf("version %q not found as tag or branch: %w", version, lastErr)
}

// ── manifest parsing ─────────────────────────────────────────────────────────

func readManifest(dir string) (Manifest, error) {
	var data []byte
	var err error
	for _, name := range []string{"action.yml", "action.yaml"} {
		data, err = os.ReadFile(filepath.Join(dir, name)) //nolint:gosec
		if err == nil {
			break
		}
	}
	if err != nil {
		return Manifest{}, fmt.Errorf("no action.yml or action.yaml found in %s", filepath.Base(dir))
	}
	return ParseManifest(data)
}

// ParseManifest parses an action.yml document.
func ParseManifest(data []byte) (Manifest, error) {
	var raw struct {
		Name        string                    `yaml:"name"`
		Description string                    `yaml:"description"`
		Inputs      map[string]map[string]any `yaml:"inputs"`
		Outputs     map[string]map[string]any `yaml:"outputs"`
		Runs        struct {
			Using      string           `yaml:"using"`
			Main       string           `yaml:"main"`
			Pre        string           `yaml:"pre"`
			PreIf      string           `yaml:"pre-if"`
			Post       string           `yaml:"post"`
			PostIf     string           `yaml:"post-if"`
			Image      string           `yaml:"image"`
			Args       []any            `yaml:"args"`
			Entrypoint string           `yaml:"entrypoint"`
			Env        map[string]any   `yaml:"env"`
			Steps      []map[string]any `yaml:"steps"`
		} `yaml:"runs"`
	}
	if err := yaml.Unmarshal(data, &raw); err != nil {
		return Manifest{}, fmt.Errorf("parse action manifest: %w", err)
	}
	m := Manifest{
		Name:        raw.Name,
		Description: raw.Description,
		Inputs:      map[string]ManifestInput{},
		Outputs:     map[string]string{},
		Runs: Runs{
			Using: raw.Runs.Using, Main: raw.Runs.Main, Pre: raw.Runs.Pre, PreIf: raw.Runs.PreIf,
			Post: raw.Runs.Post, PostIf: raw.Runs.PostIf, Image: raw.Runs.Image,
			Entrypoint: raw.Runs.Entrypoint, Env: stringMap(raw.Runs.Env),
		},
	}
	if m.Runs.Kind() == "" {
		return Manifest{}, fmt.Errorf("unsupported runs.using %q (supported: composite, node*, docker)", raw.Runs.Using)
	}
	for name, def := range raw.Inputs {
		in := ManifestInput{}
		if v, ok := def["description"]; ok {
			in.Description = scalar(v)
		}
		if v, ok := def["required"]; ok {
			in.Required = scalar(v) == "true"
		}
		if v, ok := def["default"]; ok {
			in.Default, in.HasDefault = scalar(v), true
		}
		m.Inputs[name] = in
	}
	for name, def := range raw.Outputs {
		if v, ok := def["value"]; ok {
			m.Outputs[name] = scalar(v)
		} else {
			m.Outputs[name] = ""
		}
	}
	for _, a := range raw.Runs.Args {
		m.Runs.Args = append(m.Runs.Args, scalar(a))
	}
	for _, s := range raw.Runs.Steps {
		m.Runs.Steps = append(m.Runs.Steps, compositeStep(s))
	}
	if m.Runs.Kind() == KindNode && m.Runs.Main == "" {
		return Manifest{}, fmt.Errorf("node action has no runs.main")
	}
	if m.Runs.Kind() == KindDocker && m.Runs.Image == "" {
		return Manifest{}, fmt.Errorf("docker action has no runs.image")
	}
	return m, nil
}

func compositeStep(s map[string]any) CompositeStep {
	st := CompositeStep{
		ID: scalar(s["id"]), Name: scalar(s["name"]), If: scalar(s["if"]),
		Run: scalar(s["run"]), Shell: scalar(s["shell"]), Uses: scalar(s["uses"]),
		WorkingDirectory: scalar(s["working-directory"]),
		ContinueOnError:  scalar(s["continue-on-error"]) == "true",
	}
	if w, ok := s["with"].(map[string]any); ok {
		st.With = stringMap(w)
	}
	if e, ok := s["env"].(map[string]any); ok {
		st.Env = stringMap(e)
	}
	return st
}

func stringMap(in map[string]any) map[string]string {
	if len(in) == 0 {
		return nil
	}
	out := make(map[string]string, len(in))
	for k, v := range in {
		out[k] = scalar(v)
	}
	return out
}

// scalar renders a YAML scalar the way a workflow author wrote it.
func scalar(v any) string {
	switch x := v.(type) {
	case nil:
		return ""
	case string:
		return x
	case bool:
		if x {
			return "true"
		}
		return "false"
	}
	return fmt.Sprint(v)
}
