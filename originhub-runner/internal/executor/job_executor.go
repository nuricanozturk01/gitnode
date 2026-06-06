package executor

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"go.uber.org/zap"

	"github.com/nuricanozturk/originhub-runner/internal/actions"
	jobctx "github.com/nuricanozturk/originhub-runner/internal/context"
	runnerlog "github.com/nuricanozturk/originhub-runner/internal/log"
	"github.com/nuricanozturk/originhub-runner/internal/workspace"
	"github.com/nuricanozturk/originhub-runner/pkg/protocol"
)

// JobExecutor orchestrates step execution for a single job.
type JobExecutor struct {
	workspaceMgr *workspace.Manager
	actionReg    *actions.Registry
	send         func(protocol.OutboundMessage)
	executorType string // "shell" or "docker"
	log          *zap.Logger
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
	}
}

// Execute runs the job in a goroutine and returns immediately.
func (e *JobExecutor) Execute(ctx context.Context, job protocol.JobPayload) {
	go e.run(ctx, job)
}

func (e *JobExecutor) run(ctx context.Context, job protocol.JobPayload) {
	workDir, err := e.workspaceMgr.Create(job.RunID, job.ID)
	if err != nil {
		e.log.Error("failed to create workspace", zap.Error(err))
		e.sendJobCompleted(job.ID, "failure")
		return
	}
	defer e.workspaceMgr.Remove(job.RunID, job.ID) //nolint:errcheck

	timeout := time.Duration(job.TimeoutMins) * time.Minute
	if timeout <= 0 {
		timeout = 6 * time.Hour
	}
	jobCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	jctx := jobctx.New(job.Env, job.Secrets)
	// Inject repo context so checkout knows where to clone from.
	for k, v := range job.Env {
		jctx.SetEnv(k, v)
	}

	e.sendJobClaimed(job.ID)

	jobConclusion := "success"

	for _, step := range job.Steps {
		if jobCtx.Err() != nil {
			break
		}

		streamer := runnerlog.New(
			step.ID,
			jctx.SecretValues(),
			e.send,
			e.log,
		)

		e.sendStepStarted(step.ID, job.ID)

		conclusion, execErr := e.runStep(jobCtx, step, jctx, streamer, workDir)
		if execErr != nil {
			streamer.Emit(fmt.Sprintf("step error: %v", execErr), "error")
			conclusion = "failure"
		}

		e.sendStepCompleted(step.ID, job.ID, conclusion, map[string]string{})

		if conclusion == "failure" {
			jobConclusion = "failure"
			break
		}
	}

	e.sendJobCompleted(job.ID, jobConclusion)
}

func (e *JobExecutor) runStep(
	ctx context.Context,
	step protocol.StepPayload,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (string, error) {

	if step.Uses != "" {
		handler, err := e.actionReg.Resolve(step.Uses)
		if err != nil {
			return "failure", fmt.Errorf("resolve action %q: %w", step.Uses, err)
		}
		inputs := step.With
		if inputs == nil {
			inputs = map[string]string{}
		}
		_, execErr := handler.Execute(ctx, inputs, jctx, streamer, workDir)
		if execErr != nil {
			return "failure", execErr
		}
		return "success", nil
	}

	if step.Run != "" {
		return e.runShellCommand(ctx, step.Run, jctx, streamer, workDir)
	}

	streamer.Emit("step has neither 'uses' nor 'run'", "warn")
	return "skipped", nil
}

func (e *JobExecutor) runShellCommand(
	ctx context.Context,
	command string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (string, error) {

	if e.executorType == "docker" {
		exec := NewDockerExecutor(workDir, "", e.log)
		return exec.Run(ctx, command, jctx, streamer)
	}
	exec := NewShellExecutor(workDir)
	return exec.Run(ctx, command, jctx, streamer)
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
	e.sendMsg(protocol.TypeStepCompleted, protocol.StepCompletedPayload{
		StepID:     stepID,
		JobID:      jobID,
		Conclusion: conclusion,
		Outputs:    outputs,
	})
}

func (e *JobExecutor) sendJobCompleted(jobID, conclusion string) {
	e.sendMsg(protocol.TypeJobCompleted, protocol.JobCompletedPayload{
		JobID:      jobID,
		Conclusion: conclusion,
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
