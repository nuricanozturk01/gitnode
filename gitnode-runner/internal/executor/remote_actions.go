package executor

import (
	"go.uber.org/zap"

	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/nuricanozturk/gitnode-runner/internal/actions"
	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	"github.com/nuricanozturk/gitnode-runner/internal/expr"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

// maxActionDepth bounds composite actions that use other composite actions.
const maxActionDepth = 10

// postHook is a node action's `post:` script, run when the job ends.
type postHook struct {
	la       *actions.LoadedAction
	inputs   map[string]string
	state    map[string]string
	streamer *runnerlog.Streamer
	name     string
	// run is set for built-in actions (such as actions/cache saving at the end of the job); the
	// hook then has no loaded action. successOnly skips it when the job did not succeed.
	run         func(ctx context.Context, streamer *runnerlog.Streamer) error
	successOnly bool
}

// runRemoteAction runs an action that is not built in: `owner/repo@ref` (fetched from GitHub)
// or `./path` (inside the workspace). Composite, node and docker actions are supported.
func (e *JobExecutor) runRemoteAction(
	ctx context.Context,
	sc *stepScope,
	step protocol.StepPayload,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
	jobStatus string,
) (string, map[string]string, error) {
	if sc.depth >= maxActionDepth {
		return statusFailure, nil, fmt.Errorf("actions are nested more than %d levels deep", maxActionDepth)
	}
	streamer.Emit("Fetching action "+step.Uses, "info")
	la, err := e.resolver.Load(ctx, step.Uses, workDir)
	if err != nil {
		return statusFailure, nil, err
	}
	env := buildExprEnv(sc.job, jctx, jobStatus, sc.results, workDir, rt)
	inputs, err := resolveInputs(la.Manifest, step.With, env)
	if err != nil {
		return statusFailure, nil, fmt.Errorf("action %q: %w", step.Uses, err)
	}

	switch la.Manifest.Runs.Kind() {
	case actions.KindComposite:
		return e.runComposite(ctx, sc, la, inputs, jctx, streamer, workDir, rt)
	case actions.KindNode:
		return e.runNodeAction(ctx, sc, step, la, inputs, jctx, streamer, workDir, rt, jobStatus)
	case actions.KindDocker:
		return e.runDockerAction(ctx, la, inputs, jctx, streamer, workDir, rt, env)
	}
	return statusFailure, nil, fmt.Errorf("action %q has an unsupported type", step.Uses)
}

// resolveInputs merges the step's `with:` with the action's declared defaults and enforces
// required inputs.
func resolveInputs(m actions.Manifest, with map[string]string, env expr.Env) (map[string]string, error) {
	out := map[string]string{}
	lookup := func(name string) (string, bool) {
		if v, ok := with[name]; ok {
			return v, true
		}
		for k, v := range with {
			if strings.EqualFold(k, name) {
				return v, true
			}
		}
		return "", false
	}
	names := make([]string, 0, len(m.Inputs))
	for n := range m.Inputs {
		names = append(names, n)
	}
	sort.Strings(names)
	for _, name := range names {
		def := m.Inputs[name]
		switch v, ok := lookup(name); {
		case ok:
			out[name] = v
		case def.HasDefault:
			d, _ := expr.Interpolate(def.Default, env)
			out[name] = d
		case def.Required:
			return nil, fmt.Errorf("required input %q is missing", name)
		default:
			out[name] = ""
		}
	}
	for k, v := range with {
		known := false
		for n := range m.Inputs {
			if strings.EqualFold(n, k) {
				known = true
				break
			}
		}
		if !known {
			out[k] = v
		}
	}
	return out, nil
}

// inputEnv renders inputs as INPUT_<NAME> variables the way GitHub's runner does.
func inputEnv(inputs map[string]string) map[string]string {
	out := make(map[string]string, len(inputs))
	for k, v := range inputs {
		out["INPUT_"+strings.ToUpper(strings.ReplaceAll(k, " ", "_"))] = v
	}
	return out
}

// processEnv is the OS environment plus the job env plus extra overrides (last one wins).
func processEnv(jctx *jobctx.JobContext, extra map[string]string) []string {
	env := os.Environ()
	for k, v := range jctx.Env() {
		env = append(env, k+"="+v)
	}
	keys := make([]string, 0, len(extra))
	for k := range extra {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		env = append(env, k+"="+extra[k])
	}
	return env
}

// safeJoin joins rel below dir and rejects paths that escape it.
func safeJoin(dir, rel string) (string, error) {
	full := filepath.Join(dir, filepath.FromSlash(rel))
	r, err := filepath.Rel(dir, full)
	if err != nil || r == ".." || strings.HasPrefix(r, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("path %q escapes the action directory", rel)
	}
	return full, nil
}

func runProcess(
	ctx context.Context,
	dir string,
	env []string,
	streamer *runnerlog.Streamer,
	name string,
	args ...string,
) (string, error) {
	cmd := exec.CommandContext(ctx, name, args...) //nolint:gosec
	cmd.Dir = dir
	cmd.Env = env
	cmd.Stdout = streamer
	cmd.Stderr = streamer
	defer streamer.Flush()
	if err := cmd.Run(); err != nil {
		if ctx.Err() != nil {
			return statusCancelled, nil
		}
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			streamer.Emit(fmt.Sprintf("Process exited with code %d", exitErr.ExitCode()), "error")
			return statusFailure, nil
		}
		return statusFailure, err
	}
	return statusSuccess, nil
}

func condOrDefault(cond, def string) string {
	if strings.TrimSpace(cond) == "" {
		return def
	}
	return cond
}

// ── composite ────────────────────────────────────────────────────────────────

func scopedJob(job protocol.JobPayload, inputs map[string]string, actionDir string) protocol.JobPayload {
	ctx := make(map[string]string, len(job.Context)+len(inputs)+1)
	for k, v := range job.Context {
		if !strings.HasPrefix(k, "inputs.") { // a composite action sees only its own inputs
			ctx[k] = v
		}
	}
	for k, v := range inputs {
		ctx["inputs."+k] = v
	}
	ctx["github.action_path"] = actionDir
	job.Context = ctx
	return job
}

func (e *JobExecutor) runComposite(
	ctx context.Context,
	sc *stepScope,
	la *actions.LoadedAction,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
) (string, map[string]string, error) {
	local := &stepScope{
		job:     scopedJob(sc.job, inputs, la.Dir),
		results: map[string]*stepResult{},
		depth:   sc.depth + 1,
		posts:   sc.posts,
	}
	restore := overlayEnv(jctx, map[string]string{"GITHUB_ACTION_PATH": la.Dir})
	defer restore()

	status := statusSuccess
	for _, cs := range la.Manifest.Runs.Steps {
		if ctx.Err() != nil {
			status = statusCancelled
			break
		}
		sp := protocol.StepPayload{
			Key: cs.ID, Name: cs.Name, Uses: cs.Uses, Run: cs.Run, With: cs.With, Env: cs.Env,
			Condition: cs.If, ContinueOnError: cs.ContinueOnError,
			WorkingDirectory: cs.WorkingDirectory, Shell: cs.Shell,
		}
		res := e.runOneStep(ctx, local, sp, jctx, streamer, workDir, rt, status)
		if cs.ID != "" {
			local.results[cs.ID] = res
		}
		if res.conclusion == statusFailure {
			status = statusFailure
		}
	}

	env := buildExprEnv(local.job, jctx, status, local.results, workDir, rt)
	outputs := map[string]string{}
	for name, valueExpr := range la.Manifest.Outputs {
		if valueExpr == "" {
			continue
		}
		v, err := expr.Interpolate(valueExpr, env)
		if err != nil {
			streamer.Emit(fmt.Sprintf("warning: output %q: %v", name, err), "warn")
		}
		outputs[name] = v
	}
	return status, outputs, nil
}

// ── node ─────────────────────────────────────────────────────────────────────

func (e *JobExecutor) actionEnv(
	la *actions.LoadedAction, inputs, state map[string]string,
) map[string]string {
	extra := inputEnv(inputs)
	extra["GITHUB_ACTION_PATH"] = la.Dir
	for k, v := range state {
		extra["STATE_"+k] = v
	}
	return extra
}

func (e *JobExecutor) runNodeAction(
	ctx context.Context,
	sc *stepScope,
	step protocol.StepPayload,
	la *actions.LoadedAction,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
	jobStatus string,
) (string, map[string]string, error) {
	node, err := exec.LookPath("node")
	if err != nil {
		return statusFailure, nil, fmt.Errorf(
			"%s needs Node.js but `node` was not found in PATH; add actions/setup-node before this step", step.Uses)
	}
	runs := la.Manifest.Runs
	mainPath, err := safeJoin(la.Dir, runs.Main)
	if err != nil {
		return statusFailure, nil, err
	}

	stateFile, err := os.CreateTemp(rt.dir, "state-*")
	if err != nil {
		return statusFailure, nil, fmt.Errorf("create state file: %w", err)
	}
	_ = stateFile.Close()
	defer os.Remove(stateFile.Name()) //nolint:errcheck

	state := map[string]string{}
	run := func(script string) (string, error) {
		extra := e.actionEnv(la, inputs, state)
		extra["GITHUB_STATE"] = stateFile.Name()
		concl, runErr := runProcess(ctx, workDir, processEnv(jctx, extra), streamer, node, script)
		for k, v := range parseKeyValues(drain(stateFile.Name())) {
			state[k] = v
		}
		for k, v := range streamer.State() {
			state[k] = v
		}
		return concl, runErr
	}

	if runs.Pre != "" {
		env := buildExprEnv(sc.job, jctx, jobStatus, sc.results, workDir, rt)
		if ok, _ := expr.EvalCondition(condOrDefault(runs.PreIf, "always()"), env); ok {
			prePath, joinErr := safeJoin(la.Dir, runs.Pre)
			if joinErr != nil {
				return statusFailure, nil, joinErr
			}
			if concl, runErr := run(prePath); concl != statusSuccess || runErr != nil {
				return concl, nil, runErr
			}
		}
	}

	concl, runErr := run(mainPath)

	if runs.Post != "" {
		*sc.posts = append(*sc.posts, postHook{
			la: la, inputs: inputs, state: state, streamer: streamer, name: displayName(step, la),
		})
	}
	return concl, nil, runErr
}

func displayName(step protocol.StepPayload, la *actions.LoadedAction) string {
	switch {
	case step.Name != "":
		return step.Name
	case la.Manifest.Name != "":
		return la.Manifest.Name
	}
	return step.Uses
}

// runPostHooks runs node actions' post scripts in reverse order. It runs even when the job
// failed or was cancelled, honoring each action's post-if (default always()).
func (e *JobExecutor) runPostHooks(
	ctx context.Context,
	sc *stepScope,
	jctx *jobctx.JobContext,
	workDir string,
	rt *runtimeFiles,
	status string,
) {
	if sc.posts == nil || len(*sc.posts) == 0 {
		return
	}
	pctx := ctx
	if pctx.Err() != nil {
		c, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
		defer cancel()
		pctx = c
	}
	node, nodeErr := exec.LookPath("node")
	hooks := *sc.posts
	for i := len(hooks) - 1; i >= 0; i-- {
		h := hooks[i]
		if h.run != nil {
			e.runBuiltinPostHook(pctx, h, status)
			continue
		}
		if nodeErr != nil {
			e.log.Warn("skipping post hook: node not found", zap.String("action", h.name))
			continue
		}
		env := buildExprEnv(sc.job, jctx, status, sc.results, workDir, rt)
		ok, condErr := expr.EvalCondition(condOrDefault(h.la.Manifest.Runs.PostIf, "always()"), env)
		if condErr != nil || !ok {
			continue
		}
		postPath, joinErr := safeJoin(h.la.Dir, h.la.Manifest.Runs.Post)
		if joinErr != nil {
			h.streamer.Emit(joinErr.Error(), "error")
			continue
		}
		h.streamer.Emit("Post "+h.name, "info")
		if _, runErr := runProcess(pctx, workDir,
			processEnv(jctx, e.actionEnv(h.la, h.inputs, h.state)), h.streamer, node, postPath); runErr != nil {
			h.streamer.Emit(fmt.Sprintf("post step error: %v", runErr), "error")
		}
	}
}

// runBuiltinPostHook runs the end-of-job work of a built-in action. A failure is only a warning,
// as on GitHub where a post step that cannot save a cache does not fail the job.
func (e *JobExecutor) runBuiltinPostHook(ctx context.Context, h postHook, status string) {
	if h.successOnly && status != statusSuccess {
		return
	}
	h.streamer.Emit("Post "+h.name, "info")
	if err := h.run(ctx, h.streamer); err != nil {
		h.streamer.Emit(fmt.Sprintf("post step warning: %v", err), "warn")
	}
	h.streamer.Flush()
}

// ── docker ───────────────────────────────────────────────────────────────────

func (e *JobExecutor) runDockerAction(
	ctx context.Context,
	la *actions.LoadedAction,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
	env expr.Env,
) (string, map[string]string, error) {
	docker, err := exec.LookPath("docker")
	if err != nil {
		return statusFailure, nil, errors.New("docker actions need the docker CLI, which was not found in PATH")
	}
	runs := la.Manifest.Runs
	image := runs.Image
	if after, ok := strings.CutPrefix(image, "docker://"); ok {
		image = after
	} else {
		dockerfile, joinErr := safeJoin(la.Dir, image)
		if joinErr != nil {
			return statusFailure, nil, joinErr
		}
		sum := sha256.Sum256([]byte(la.Dir))
		tag := "gitnode-action-" + hex.EncodeToString(sum[:6])
		streamer.Emit("Building action image "+tag, "info")
		concl, buildErr := runProcess(ctx, la.Dir, os.Environ(), streamer,
			docker, "build", "-t", tag, "-f", dockerfile, la.Dir)
		if concl != statusSuccess || buildErr != nil {
			return concl, nil, buildErr
		}
		image = tag
	}

	extra := inputEnv(inputs)
	extra["GITHUB_ACTION_PATH"] = la.Dir
	extra["GITHUB_WORKSPACE"] = "/github/workspace"
	for k, v := range runs.Env {
		iv, _ := expr.Interpolate(v, env)
		extra[k] = iv
	}
	envMap := jctx.Env()
	for k, v := range extra {
		envMap[k] = v
	}
	envMap["GITHUB_WORKSPACE"] = "/github/workspace"

	args := []string{"run", "--rm", "--workdir", "/github/workspace",
		"-v", workDir + ":/github/workspace", "-v", rt.dir + ":" + rt.dir}
	keys := make([]string, 0, len(envMap))
	for k := range envMap {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		args = append(args, "-e", k+"="+envMap[k])
	}
	if runs.Entrypoint != "" {
		args = append(args, "--entrypoint", runs.Entrypoint)
	}
	args = append(args, image)
	for _, a := range runs.Args {
		ia, _ := expr.Interpolate(a, env)
		args = append(args, ia)
	}
	concl, runErr := runProcess(ctx, workDir, os.Environ(), streamer, docker, args...)
	return concl, nil, runErr
}
