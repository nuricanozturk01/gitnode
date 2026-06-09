package actions

import (
	"context"
	"fmt"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
)

// ActionHandler executes a single built-in action (e.g. actions/checkout@v1).
type ActionHandler interface {
	// Name returns the action identifier without version (e.g. "actions/checkout").
	Name() string
	// Versions returns the supported version tags (e.g. ["v1"]).
	Versions() []string
	// Execute runs the action and returns any step outputs.
	Execute(
		ctx context.Context,
		inputs map[string]string,
		jctx *jobctx.JobContext,
		streamer *runnerlog.Streamer,
		workDir string,
	) (outputs map[string]string, err error)
}

// Registry maps "name@version" strings to ActionHandler implementations.
type Registry struct {
	handlers map[string]ActionHandler
}

// NewRegistry builds a Registry with all built-in handlers registered.
func NewRegistry(serverURL, runnerToken string) *Registry {
	r := &Registry{handlers: make(map[string]ActionHandler)}
	r.register(NewCheckoutAction(serverURL, runnerToken))
	r.register(NewSetupNodeAction())
	r.register(NewSetupPnpmAction())
	r.register(NewSetupJavaAction())
	r.register(NewSetupPythonAction())
	r.register(NewSetupGoAction())
	r.register(NewDockerLoginAction())
	r.register(NewCacheAction(serverURL, runnerToken))
	r.register(NewUploadArtifactAction(serverURL, runnerToken))
	r.register(NewDownloadArtifactAction(serverURL, runnerToken))
	return r
}

func (r *Registry) register(h ActionHandler) {
	for _, ver := range h.Versions() {
		key := h.Name() + "@" + ver
		r.handlers[key] = h
	}
}

// Resolve returns the handler for a "name@version" string.
// Returns an error when the action is unknown.
func (r *Registry) Resolve(uses string) (ActionHandler, error) {
	h, ok := r.handlers[uses]
	if !ok {
		return nil, fmt.Errorf("unknown action: %q", uses)
	}
	return h, nil
}
