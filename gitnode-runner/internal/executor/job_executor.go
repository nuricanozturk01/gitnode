package executor

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/internal/actions"
	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	"github.com/nuricanozturk/gitnode-runner/internal/expr"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/workspace"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

const (
	statusSuccess   = "success"
	statusFailure   = "failure"
	statusCancelled = "cancelled"
	statusSkipped   = "skipped"
)

// JobExecutor orchestrates step execution for a single job.
type JobExecutor struct {
	workspaceMgr *workspace.Manager
	actionReg    *actions.Registry
	send         func(protocol.OutboundMessage)
	executorType string // "shell" or "docker"
	log          *zap.Logger
	resolver     *actions.Resolver
	serverURL    string // the server the runner is connected to; github.server_url fallback
}

// New builds a JobExecutor.
func New(
	workspaceMgr *workspace.Manager,
	actionReg *actions.Registry,
	send func(protocol.OutboundMessage),
	executorType string,
	log *zap.Logger,
) *JobExecutor {
	return &JobExecutor{
		workspaceMgr: workspaceMgr,
		actionReg:    actionReg,
		send:         send,
		executorType: executorType,
		log:          log,
		resolver:     actions.NewResolver(filepath.Join(os.TempDir(), "gitnode-runner-actions")),
	}
}

// WithServerURL tells the executor which server it serves, used for GITHUB_SERVER_URL and
// GITHUB_API_URL when the server does not send its public address.
func (e *JobExecutor) WithServerURL(url string) *JobExecutor {
	e.serverURL = strings.TrimRight(url, "/")
	return e
}

// Execute runs the job in a goroutine and returns immediately.
func (e *JobExecutor) Execute(ctx context.Context, job protocol.JobPayload) {
	go e.run(ctx, job)
}

// stepResult is what later steps and expressions can observe about a finished step.
type stepResult struct {
	outputs    map[string]string
	outcome    string // result before continue-on-error: success, failure, cancelled, skipped
	conclusion string // result after continue-on-error
}

// stepScope is the expression scope steps run in: the job itself, or one composite action
// (its own inputs.* and steps.*).
type stepScope struct {
	job     protocol.JobPayload
	results map[string]*stepResult
	depth   int
	posts   *[]postHook
}

func (e *JobExecutor) run(ctx context.Context, job protocol.JobPayload) {
	workDir, err := e.workspaceMgr.Create(job.RunID, job.ID)
	if err != nil {
		e.log.Error("failed to create workspace", zap.Error(err))
		e.sendJobCompleted(job.ID, statusFailure, nil)
		return
	}
	defer e.workspaceMgr.Remove(job.RunID, job.ID) //nolint:errcheck

	rt, err := newRuntimeFiles()
	if err != nil {
		e.log.Error("failed to create runtime files", zap.Error(err))
		e.sendJobCompleted(job.ID, statusFailure, nil)
		return
	}
	defer rt.cleanup()

	timeout := time.Duration(job.TimeoutMins) * time.Minute
	if timeout <= 0 {
		timeout = 6 * time.Hour
	}
	jobCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	if e.serverURL != "" {
		job.Context = withDefault(job.Context, "github.server_url", e.serverURL)
	}
	jctx := jobctx.New(job.Env, job.Secrets)
	applyStandardEnv(jctx, job, workDir, rt)
	if eventPath, eventErr := writeEventFile(rt.dir, job.Event); eventErr != nil {
		e.log.Warn("could not write the event file", zap.Error(eventErr))
	} else {
		jctx.SetEnv("GITHUB_EVENT_PATH", eventPath)
	}

	e.sendJobClaimed(job.ID)

	status := statusSuccess
	results := map[string]*stepResult{}
	var posts []postHook
	sc := &stepScope{job: job, results: results, posts: &posts}

	for _, step := range job.Steps {
		if jobCtx.Err() != nil {
			status = statusCancelled
			break
		}

		streamer := runnerlog.New(step.ID, jctx.SecretValues(), e.send, e.log)
		streamer.SetDebug(stepDebugEnabled(jctx))
		e.sendStepStarted(step.ID, job.ID)

		res := e.runOneStep(jobCtx, sc, step, jctx, streamer, workDir, rt, status)

		if step.Key != "" {
			results[step.Key] = res
		}
		e.sendStepCompleted(step.ID, job.ID, res.conclusion, res.outputs)

		switch {
		case res.conclusion == statusFailure:
			status = statusFailure
		case res.outcome == statusCancelled && jobCtx.Err() != nil:
			status = statusCancelled
		}
	}

	if jobCtx.Err() != nil && status == statusSuccess {
		status = statusCancelled
	}
	if errors.Is(jobCtx.Err(), context.DeadlineExceeded) && ctx.Err() == nil {
		status = statusFailure // the job ran past its timeout
	}

	e.runPostHooks(ctx, sc, jctx, workDir, rt, status)
	outputs := evalJobOutputs(job, jctx, status, results, workDir, rt)
	e.sendJobCompleted(job.ID, status, outputs)
}

// runOneStep evaluates the step's condition, applies its env, runs it and collects its outputs.
// It never returns a nil result.
func (e *JobExecutor) runOneStep(
	ctx context.Context,
	sc *stepScope,
	step protocol.StepPayload,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
	jobStatus string,
) *stepResult {
	res := &stepResult{outputs: map[string]string{}}

	env := buildExprEnv(sc.job, jctx, jobStatus, sc.results, workDir, rt)

	run, condErr := expr.EvalCondition(step.Condition, env)
	if condErr != nil {
		streamer.Emit(fmt.Sprintf("invalid if condition %q: %v", step.Condition, condErr), "error")
		res.outcome, res.conclusion = statusFailure, statusFailure
		return res
	}
	if !run {
		streamer.Emit("Skipped: condition not met", "info")
		res.outcome, res.conclusion = statusSkipped, statusSkipped
		return res
	}

	step = interpolateStep(step, env, streamer)

	stepCtx := ctx
	if step.TimeoutMins > 0 {
		var cancel context.CancelFunc
		stepCtx, cancel = context.WithTimeout(ctx, time.Duration(step.TimeoutMins)*time.Minute)
		defer cancel()
	}

	restore := overlayEnv(jctx, withActionEnv(step))
	defer restore()

	conclusion, outputs, execErr := e.runStep(stepCtx, sc, step, jctx, streamer, workDir, rt, jobStatus)
	if execErr != nil {
		streamer.Emit(fmt.Sprintf("step error: %v", execErr), "error")
		conclusion = statusFailure
	}
	if conclusion == statusFailure && errors.Is(stepCtx.Err(), context.DeadlineExceeded) && ctx.Err() == nil {
		streamer.Emit(fmt.Sprintf("step timed out after %d minute(s)", step.TimeoutMins), "error")
	}

	for k, v := range outputs {
		res.outputs[k] = v
	}
	e.applyStreamerCommands(jctx, streamer, res)
	e.collectRuntimeFiles(jctx, rt, res, streamer)

	res.outcome = conclusion
	res.conclusion = conclusion
	if conclusion == statusFailure && step.ContinueOnError {
		streamer.Emit("Step failed but continue-on-error is set; the job keeps going", "warn")
		res.conclusion = statusSuccess
	}
	return res
}

func (e *JobExecutor) runStep(
	ctx context.Context,
	sc *stepScope,
	step protocol.StepPayload,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
	jobStatus string,
) (string, map[string]string, error) {
	if step.Uses != "" {
		handler, ok := e.actionReg.Lookup(step.Uses)
		if !ok {
			return e.runRemoteAction(ctx, sc, step, jctx, streamer, workDir, rt, jobStatus)
		}
		inputs := step.With
		if inputs == nil {
			inputs = map[string]string{}
		}
		outputs, execErr := handler.Execute(ctx, inputs, jctx, streamer, workDir)
		if execErr != nil {
			return statusFailure, outputs, execErr
		}
		e.registerBuiltinPostHook(sc, handler, step, inputs, outputs, jctx, streamer, workDir)
		return statusSuccess, outputs, nil
	}
	if step.Run != "" {
		conclusion, err := e.runShellCommand(ctx, step, jctx, streamer, workDir, rt)
		return conclusion, nil, err
	}
	streamer.Emit("step has neither 'uses' nor 'run'", "warn")
	return statusSkipped, nil, nil
}

// registerBuiltinPostHook lets a built-in action (actions/cache) do work when the job ends.
func (e *JobExecutor) registerBuiltinPostHook(
	sc *stepScope,
	handler actions.ActionHandler,
	step protocol.StepPayload,
	inputs, outputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) {
	hooker, ok := handler.(actions.PostHooker)
	if !ok || sc.posts == nil {
		return
	}
	if fn := hooker.PostHook(inputs, outputs, jctx, workDir); fn != nil {
		*sc.posts = append(*sc.posts, postHook{
			streamer:    streamer,
			name:        step.Uses,
			run:         fn,
			successOnly: true,
		})
	}
}

func (e *JobExecutor) runShellCommand(
	ctx context.Context,
	step protocol.StepPayload,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
	rt *runtimeFiles,
) (string, error) {
	subDir, err := resolveSubDir(workDir, step.WorkingDirectory)
	if err != nil {
		return statusFailure, err
	}
	if e.executorType == "docker" {
		if filepath.IsAbs(subDir) {
			return statusFailure, fmt.Errorf(
				"working-directory %q is outside the workspace, which the docker executor cannot reach", subDir)
		}
		exec := NewDockerExecutor(workDir, "", e.log).
			WithShell(step.Shell).
			WithSubDir(subDir).
			WithSharedPaths(rt.dir)
		return exec.Run(ctx, step.Run, jctx, streamer)
	}
	dir := subDir
	if !filepath.IsAbs(dir) {
		dir = filepath.Join(workDir, subDir)
	}
	exec := NewShellExecutor(dir).WithShell(step.Shell)
	return exec.Run(ctx, step.Run, jctx, streamer)
}

// applyStreamerCommands takes over what the step's workflow commands produced: outputs from the
// deprecated ::set-output, and ::add-mask values that later steps must hide too.
func (e *JobExecutor) applyStreamerCommands(
	jctx *jobctx.JobContext, streamer *runnerlog.Streamer, res *stepResult,
) {
	streamer.Flush()
	streamer.ClosePendingGroups()
	for k, v := range streamer.Outputs() {
		res.outputs[k] = v
	}
	for _, m := range streamer.NewMasks() {
		jctx.AddMask(m)
	}
}

// stepDebugEnabled is GitHub's step debug switch: the ACTIONS_STEP_DEBUG secret or variable.
func stepDebugEnabled(jctx *jobctx.JobContext) bool {
	if jctx.Secrets()["ACTIONS_STEP_DEBUG"] == "true" {
		return true
	}
	return jctx.Env()["ACTIONS_STEP_DEBUG"] == "true"
}

// emitSummary sends the markdown a step wrote to $GITHUB_STEP_SUMMARY as log lines of level
// "summary"; the UI shows them in the job summary instead of the log.
func emitSummary(streamer *runnerlog.Streamer, content string) {
	content = strings.TrimRight(content, "\r\n")
	if strings.TrimSpace(content) == "" {
		return
	}
	for _, line := range strings.Split(content, "\n") {
		streamer.Emit(strings.TrimSuffix(line, "\r"), "summary")
	}
}

// collectRuntimeFiles applies what the step wrote to $GITHUB_OUTPUT, $GITHUB_ENV and
// $GITHUB_PATH, and forwards $GITHUB_STEP_SUMMARY.
func (e *JobExecutor) collectRuntimeFiles(
	jctx *jobctx.JobContext, rt *runtimeFiles, res *stepResult, streamer *runnerlog.Streamer,
) {
	for k, v := range parseKeyValues(drain(rt.output)) {
		res.outputs[k] = v
	}
	for k, v := range parseKeyValues(drain(rt.env)) {
		jctx.SetEnv(k, v)
	}
	if entries := parsePathEntries(drain(rt.path)); len(entries) > 0 {
		current, ok := jctx.Env()["PATH"]
		if !ok {
			current = os.Getenv("PATH")
		}
		jctx.SetEnv("PATH", strings.Join(entries, string(os.PathListSeparator))+
			string(os.PathListSeparator)+current)
	}
	emitSummary(streamer, drain(rt.summary))
}

// interpolateStep resolves ${{ }} expressions left in the step by the server (steps.*, env.*,
// secrets.*, runner.*, ...). An expression that fails to evaluate is kept verbatim and logged.
func interpolateStep(step protocol.StepPayload, env expr.Env, streamer *runnerlog.Streamer) protocol.StepPayload {
	apply := func(s string) string {
		out, err := expr.Interpolate(s, env)
		if err != nil {
			streamer.Emit(fmt.Sprintf("warning: %v", err), "warn")
		}
		return out
	}
	step.Run = apply(step.Run)
	step.WorkingDirectory = apply(step.WorkingDirectory)
	step.Shell = apply(step.Shell)
	step.With = mapValues(step.With, apply)
	step.Env = mapValues(step.Env, apply)
	return step
}

func mapValues(in map[string]string, f func(string) string) map[string]string {
	if in == nil {
		return nil
	}
	out := make(map[string]string, len(in))
	for k, v := range in {
		out[k] = f(v)
	}
	return out
}

// evalJobOutputs evaluates the job's `outputs:` templates against the finished steps. An
// expression that cannot be evaluated yields an empty value, like GitHub.
func evalJobOutputs(
	job protocol.JobPayload,
	jctx *jobctx.JobContext,
	status string,
	results map[string]*stepResult,
	workDir string,
	rt *runtimeFiles,
) map[string]string {
	if len(job.Outputs) == 0 {
		return nil
	}
	env := buildExprEnv(job, jctx, status, results, workDir, rt)
	out := make(map[string]string, len(job.Outputs))
	for name, template := range job.Outputs {
		value, err := expr.Interpolate(template, env)
		if err != nil {
			value = ""
		}
		out[name] = value
	}
	return out
}

// withActionEnv adds GITHUB_ACTION (the step's id, or a generated name) to the step's env.
func withActionEnv(step protocol.StepPayload) map[string]string {
	out := make(map[string]string, len(step.Env)+1)
	for k, v := range step.Env {
		out[k] = v
	}
	if _, ok := out["GITHUB_ACTION"]; !ok {
		name := step.Key
		if name == "" {
			name = fmt.Sprintf("__run_%d", step.Number)
		}
		out["GITHUB_ACTION"] = name
	}
	return out
}

// overlayEnv applies step-scoped env on top of the job env and returns a function restoring the
// previous values. Steps run sequentially within a job, so the shared context is safe to patch.
func overlayEnv(jctx *jobctx.JobContext, overlay map[string]string) func() {
	if len(overlay) == 0 {
		return func() {}
	}
	prev := jctx.Env()
	for k, v := range overlay {
		jctx.SetEnv(k, v)
	}
	return func() {
		for k := range overlay {
			if old, had := prev[k]; had {
				jctx.SetEnv(k, old)
			} else {
				jctx.UnsetEnv(k)
			}
		}
	}
}

// resolveSubDir resolves a step's working-directory like GitHub: relative paths are inside the
// workspace (created when missing), absolute paths must already exist. It returns the directory
// relative to the workspace, or an absolute path when the directory is outside it.
func resolveSubDir(workDir, wd string) (string, error) {
	wd = strings.TrimSpace(wd)
	if wd == "" {
		return "", nil
	}
	if filepath.IsAbs(wd) {
		return existingDir(filepath.Clean(wd), wd)
	}
	target := filepath.Join(workDir, wd)
	rel, err := filepath.Rel(workDir, target)
	if err != nil {
		return "", fmt.Errorf("working-directory %q: %w", wd, err)
	}
	if rel == "." {
		return "", nil
	}
	if rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return existingDir(target, wd)
	}
	if err := os.MkdirAll(target, 0o750); err != nil {
		return "", fmt.Errorf("working-directory %q: %w", wd, err)
	}
	return rel, nil
}

// existingDir returns path when it is an existing directory.
func existingDir(path, original string) (string, error) {
	info, err := os.Stat(path)
	if err != nil || !info.IsDir() {
		return "", fmt.Errorf("working-directory %q does not exist", original)
	}
	return path, nil
}

// applyStandardEnv sets the GitHub-compatible environment variables unless the workflow
// already defines them.
func applyStandardEnv(jctx *jobctx.JobContext, job protocol.JobPayload, workDir string, rt *runtimeFiles) {
	existing := jctx.Env()
	set := func(k, v string) {
		if v == "" {
			return
		}
		if _, ok := existing[k]; !ok {
			jctx.SetEnv(k, v)
		}
	}
	owner, repo := existing["GITNODE_OWNER"], existing["GITNODE_REPO"]
	ref := existing["GITNODE_REF"]
	refName, refType := splitRef(ref)

	set("CI", "true")
	set("GITHUB_ACTIONS", "true")
	set("GITNODE_ACTIONS", "true")
	set("GITHUB_WORKSPACE", workDir)
	set("GITHUB_OUTPUT", rt.output)
	set("GITHUB_ENV", rt.env)
	set("GITHUB_PATH", rt.path)
	set("GITHUB_STEP_SUMMARY", rt.summary)
	set("GITHUB_SHA", existing["GITNODE_SHA"])
	set("GITHUB_REF", ref)
	set("GITHUB_REF_NAME", refName)
	set("GITHUB_REF_TYPE", refType)
	if owner != "" && repo != "" {
		set("GITHUB_REPOSITORY", owner+"/"+repo)
	}
	set("GITHUB_REPOSITORY_OWNER", owner)
	set("GITHUB_RUN_ID", job.RunID)
	set("GITNODE_RUN_ID", job.RunID)
	set("GITNODE_JOB_ID", job.ID)
	set("GITHUB_JOB", job.Context["github.job"])
	set("GITHUB_WORKFLOW", job.Context["github.workflow"])
	set("GITHUB_RUN_NUMBER", job.Context["github.run_number"])
	set("GITHUB_EVENT_NAME", job.Context["github.event_name"])
	set("GITHUB_SERVER_URL", job.Context["github.server_url"])
	set("GITHUB_ACTOR", job.Context["github.actor"])
	set("GITHUB_TRIGGERING_ACTOR", job.Context["github.actor"])
	set("GITHUB_HEAD_REF", job.Context["github.head_ref"])
	set("GITHUB_BASE_REF", job.Context["github.base_ref"])
	set("GITHUB_RUN_ATTEMPT", firstNonEmpty(job.Context["github.run_attempt"], "1"))
	set("GITHUB_API_URL", firstNonEmpty(job.Context["github.api_url"], apiURL(job.Context["github.server_url"])))
	set("GITHUB_GRAPHQL_URL", firstNonEmpty(job.Context["github.graphql_url"], job.Context["github.api_url"]))
	set("GITHUB_RETENTION_DAYS", "90")
	set("RUNNER_NAME", runnerName())
	set("RUNNER_ENVIRONMENT", "self-hosted")
	set("RUNNER_TOOL_CACHE", toolCacheDir())
	set("RUNNER_OS", runnerOS())
	set("RUNNER_ARCH", runnerArch())
	set("RUNNER_TEMP", rt.dir)
}

// withDefault returns the context with key set to value unless the context already has it.
func withDefault(ctx map[string]string, key, value string) map[string]string {
	out := make(map[string]string, len(ctx)+1)
	for k, v := range ctx {
		out[k] = v
	}
	if out[key] == "" {
		out[key] = value
	}
	return out
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

func apiURL(serverURL string) string {
	if serverURL == "" {
		return ""
	}
	return strings.TrimRight(serverURL, "/") + "/api"
}

func runnerName() string {
	if name := os.Getenv("GITNODE_RUNNER_NAME"); name != "" {
		return name
	}
	host, err := os.Hostname()
	if err != nil {
		return "gitnode-runner"
	}
	return host
}

// toolCacheDir is where setup-* actions keep downloaded tool versions between jobs.
func toolCacheDir() string {
	dir := os.Getenv("RUNNER_TOOL_CACHE")
	if dir == "" {
		dir = filepath.Join(os.TempDir(), "gitnode-runner-toolcache")
	}
	_ = os.MkdirAll(dir, 0o750)
	return dir
}

func splitRef(ref string) (name, kind string) {
	switch {
	case strings.HasPrefix(ref, "refs/heads/"):
		return strings.TrimPrefix(ref, "refs/heads/"), "branch"
	case strings.HasPrefix(ref, "refs/tags/"):
		return strings.TrimPrefix(ref, "refs/tags/"), "tag"
	}
	return ref, ""
}

func runnerOS() string {
	switch runtime.GOOS {
	case "darwin":
		return "macOS"
	case "windows":
		return "Windows"
	}
	return "Linux"
}

func runnerArch() string {
	switch runtime.GOARCH {
	case "amd64":
		return "X64"
	case "arm64":
		return "ARM64"
	case "arm":
		return "ARM"
	case "386":
		return "X86"
	}
	return runtime.GOARCH
}

// buildExprEnv assembles every context an expression can reference.
func buildExprEnv(
	job protocol.JobPayload,
	jctx *jobctx.JobContext,
	status string,
	results map[string]*stepResult,
	workDir string,
	rt *runtimeFiles,
) expr.Env {
	vars := map[string]string{}
	for k, v := range job.Context {
		vars[k] = v
	}
	for k, v := range flattenEvent(job.Event) {
		if _, ok := vars[k]; !ok {
			vars[k] = v
		}
	}
	envMap := jctx.Env()
	for k, v := range envMap {
		vars["env."+k] = v
	}
	for k, v := range jctx.Secrets() {
		vars["secrets."+k] = v
	}
	// github.* fall back to what the runner already knows when the server did not send them.
	fallback := func(k, v string) {
		if _, ok := vars[k]; !ok && v != "" {
			vars[k] = v
		}
	}
	fallback("github.sha", envMap["GITNODE_SHA"])
	fallback("github.ref", envMap["GITNODE_REF"])
	refName, refType := splitRef(envMap["GITNODE_REF"])
	fallback("github.ref_name", refName)
	fallback("github.ref_type", refType)
	fallback("github.run_id", job.RunID)
	fallback("github.workspace", workDir)
	if owner, repo := envMap["GITNODE_OWNER"], envMap["GITNODE_REPO"]; owner != "" && repo != "" {
		fallback("github.repository", owner+"/"+repo)
		fallback("github.repository_owner", owner)
	}
	for id, r := range results {
		for k, v := range r.outputs {
			vars["steps."+id+".outputs."+k] = v
		}
		vars["steps."+id+".outcome"] = r.outcome
		vars["steps."+id+".conclusion"] = r.conclusion
	}
	vars["job.status"] = status
	vars["runner.os"] = runnerOS()
	vars["runner.arch"] = runnerArch()
	vars["runner.temp"] = rt.dir
	vars["runner.workspace"] = workDir

	return expr.Env{Vars: vars, Status: status, WorkDir: workDir}
}

// ── WS message senders ───────────────────────────────────────────────────────

func (e *JobExecutor) sendJobClaimed(jobID string) {
	e.sendMsg(protocol.TypeJobClaimed, protocol.JobClaimedPayload{JobID: jobID})
}

func (e *JobExecutor) sendStepStarted(stepID, jobID string) {
	e.sendMsg(protocol.TypeStepStarted, protocol.StepStartedPayload{StepID: stepID, JobID: jobID})
}

func (e *JobExecutor) sendStepCompleted(
	stepID, jobID, conclusion string, outputs map[string]string,
) {
	if outputs == nil {
		outputs = map[string]string{}
	}
	e.sendMsg(protocol.TypeStepCompleted, protocol.StepCompletedPayload{
		StepID:     stepID,
		JobID:      jobID,
		Conclusion: conclusion,
		Outputs:    outputs,
	})
}

func (e *JobExecutor) sendJobCompleted(jobID, conclusion string, outputs map[string]string) {
	e.sendMsg(protocol.TypeJobCompleted, protocol.JobCompletedPayload{
		JobID:      jobID,
		Conclusion: conclusion,
		Outputs:    outputs,
	})
}

func (e *JobExecutor) sendMsg(msgType protocol.MessageType, payload any) {
	data, err := json.Marshal(payload)
	if err != nil {
		e.log.Error("failed to marshal WS message", zap.Error(err))
		return
	}
	e.send(protocol.OutboundMessage{Type: msgType, Data: data})
}
