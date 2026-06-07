package context

import (
	"sync"
)

// JobContext holds runtime state for a single job execution.
// All methods are safe for concurrent use.
type JobContext struct {
	mu      sync.RWMutex
	env     map[string]string // workflow-level + job-level env vars
	secrets map[string]string // decrypted secrets (never logged)
	outputs map[string]string // step outputs keyed by "stepId.outputName"
}

// New creates a JobContext pre-populated with env and secrets.
func New(env, secrets map[string]string) *JobContext {
	merged := make(map[string]string, len(env))
	for k, v := range env {
		merged[k] = v
	}
	secretsCopy := make(map[string]string, len(secrets))
	for k, v := range secrets {
		secretsCopy[k] = v
	}
	return &JobContext{
		env:     merged,
		secrets: secretsCopy,
		outputs: make(map[string]string),
	}
}

// Env returns a snapshot of all environment variables.
func (c *JobContext) Env() map[string]string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make(map[string]string, len(c.env))
	for k, v := range c.env {
		out[k] = v
	}
	return out
}

// Secrets returns a snapshot of all secrets (caller must not log these).
func (c *JobContext) Secrets() map[string]string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make(map[string]string, len(c.secrets))
	for k, v := range c.secrets {
		out[k] = v
	}
	return out
}

// SetEnv adds or overrides a single env variable.
func (c *JobContext) SetEnv(key, value string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.env[key] = value
}

// SetOutput records a step output value.
func (c *JobContext) SetOutput(stepID, name, value string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.outputs[stepID+"."+name] = value
}

// GetOutput retrieves a step output value; returns "" if not set.
func (c *JobContext) GetOutput(stepID, name string) string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.outputs[stepID+"."+name]
}

// SecretValues returns only the secret values (for log masking).
func (c *JobContext) SecretValues() []string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	vals := make([]string, 0, len(c.secrets))
	for _, v := range c.secrets {
		if v != "" {
			vals = append(vals, v)
		}
	}
	return vals
}
