package actions

import (
	"context"
	"fmt"
	"strings"

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
	// byName finds a built-in by action name regardless of version, so workflows written for
	// newer GitHub releases (checkout@v6, setup-node@v7, ...) keep working.
	byName map[string]ActionHandler
}

// NewRegistry builds a Registry with all built-in handlers registered.
func NewRegistry(serverURL, runnerToken string) *Registry {
	r := &Registry{handlers: make(map[string]ActionHandler), byName: make(map[string]ActionHandler)}
	r.register(NewCheckoutAction(serverURL, runnerToken))
	r.register(NewSetupNodeAction())
	r.register(NewSetupPnpmAction())
	r.register(NewSetupJavaAction())
	r.register(NewSetupPythonAction())
	r.register(NewSetupGoAction())
	r.register(NewDockerLoginAction())
	r.register(NewSetupBuildxAction())
	r.register(NewDockerBuildPushAction())
	r.register(NewCacheAction(serverURL, runnerToken))
	r.register(NewUploadArtifactAction(serverURL, runnerToken))
	r.register(NewDownloadArtifactAction(serverURL, runnerToken))
	return r
}

func (r *Registry) register(h ActionHandler) {
	r.byName[h.Name()] = h
	for _, ver := range h.Versions() {
		key := h.Name() + "@" + ver
		r.handlers[key] = h
	}
}

// Resolve returns the built-in handler for a "name@version" string. A built-in matches any
// version of its name. Returns an error when no built-in has that name.
func (r *Registry) Resolve(uses string) (ActionHandler, error) {
	if h, ok := r.Lookup(uses); ok {
		return h, nil
	}
	return nil, fmt.Errorf("unknown action: %q", uses)
}

// Lookup is Resolve without the error, for callers that fall back to remote actions.
func (r *Registry) Lookup(uses string) (ActionHandler, bool) {
	if h, ok := r.handlers[uses]; ok {
		return h, true
	}
	name := uses
	if at := strings.LastIndex(uses, "@"); at > 0 {
		name = uses[:at]
	}
	h, ok := r.byName[name]
	return h, ok
}
