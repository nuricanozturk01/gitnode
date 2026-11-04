package protocol

import "encoding/json"

type MessageType string

const (
	// Client → Server
	TypeRegister         MessageType = "REGISTER"
	TypeHeartbeat        MessageType = "HEARTBEAT"
	TypeJobClaimed       MessageType = "JOB_CLAIMED"
	TypeStepStarted      MessageType = "STEP_STARTED"
	TypeLog              MessageType = "LOG"
	TypeStepCompleted    MessageType = "STEP_COMPLETED"
	TypeJobCompleted     MessageType = "JOB_COMPLETED"
	TypeArtifactUploaded MessageType = "ARTIFACT_UPLOADED"

	// Server → Client
	TypeJobAssigned  MessageType = "JOB_ASSIGNED"
	TypeJobCancelled MessageType = "JOB_CANCELLED"
	TypePing         MessageType = "PING"
)

type OutboundMessage struct {
	Type MessageType     `json:"type"`
	Data json.RawMessage `json:"data,omitempty"`
}

type InboundMessage struct {
	Type MessageType     `json:"type"`
	Data json.RawMessage `json:"data,omitempty"`
}

// Outbound payloads (client → server)

type RegisterPayload struct {
	RunnerID string `json:"runnerId"`
	Token    string `json:"token"`
}

type JobClaimedPayload struct {
	JobID    string `json:"jobId"`
	RunnerID string `json:"runnerId"`
}

type StepStartedPayload struct {
	StepID string `json:"stepId"`
	JobID  string `json:"jobId"`
}

type LogPayload struct {
	StepID  string `json:"stepId"`
	Line    int    `json:"line"`
	Content string `json:"content"`
	Level   string `json:"level"`
}

type StepCompletedPayload struct {
	StepID     string            `json:"stepId"`
	JobID      string            `json:"jobId"`
	Conclusion string            `json:"conclusion"`
	Outputs    map[string]string `json:"outputs,omitempty"`
}

type JobCompletedPayload struct {
	JobID      string `json:"jobId"`
	Conclusion string `json:"conclusion"`
}

type ArtifactUploadedPayload struct {
	JobID      string `json:"jobId"`
	ArtifactID string `json:"artifactId"`
}

// Inbound payloads (server → client)

type JobAssignedPayload struct {
	Job JobPayload `json:"job"`
}

type JobCancelledPayload struct {
	JobID string `json:"jobId"`
}

type JobPayload struct {
	ID           string            `json:"id"`
	RunID        string            `json:"runId"`
	Name         string            `json:"name"`
	RunnerLabels []string          `json:"runnerLabels"`
	Steps        []StepPayload     `json:"steps"`
	Services     []ServicePayload  `json:"services"`
	Env          map[string]string `json:"env"`
	Secrets      map[string]string `json:"secrets"`
	MatrixValues map[string]string `json:"matrixValues"`
	Needs        []string          `json:"needs"`
	TimeoutMins  int               `json:"timeoutMinutes"`
	// Context carries the flattened expression context resolved by the server
	// (github.*, inputs.*, matrix.*, needs.<job>.result, ...). The runner adds env.*,
	// secrets.*, steps.*, job.* and runner.* itself.
	Context map[string]string `json:"context"`
}

type StepPayload struct {
	ID string `json:"id"`
	// Key is the step's `id:` from the workflow YAML (empty when not set). It addresses the
	// step in expressions: steps.<key>.outputs.<name>.
	Key       string            `json:"key"`
	Number    int               `json:"number"`
	Name      string            `json:"name"`
	Uses      string            `json:"uses"`
	Run       string            `json:"run"`
	With      map[string]string `json:"with"`
	Env       map[string]string `json:"env"`
	Condition string            `json:"condition"`
	// ContinueOnError makes a failing step not fail the job (outcome=failure, conclusion=success).
	ContinueOnError bool `json:"continueOnError"`
	// WorkingDirectory is relative to the workspace root.
	WorkingDirectory string `json:"workingDirectory"`
	// Shell selects the interpreter for run steps (bash, sh, python, pwsh).
	Shell string `json:"shell"`
	// TimeoutMins limits a single step; 0 means no step-level limit.
	TimeoutMins int `json:"timeoutMinutes"`
}

type ServicePayload struct {
	Name  string            `json:"name"`
	Image string            `json:"image"`
	Env   map[string]string `json:"env"`
	Ports []string          `json:"ports"`
}
