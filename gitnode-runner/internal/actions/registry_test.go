package actions

import (
	"testing"
)

func TestRegistry_ResolveKnownActions(t *testing.T) {
	reg := NewRegistry("http://localhost:8080", "test-token")

	known := []string{
		"actions/checkout@v1",
		"actions/setup-node@v1",
		"pnpm/action-setup@v1",
		"actions/setup-java@v1",
		"docker/login-action@v1",
		"actions/cache@v1",
		"actions/upload-artifact@v1",
		"actions/download-artifact@v1",
	}

	for _, uses := range known {
		t.Run(uses, func(t *testing.T) {
			handler, err := reg.Resolve(uses)
			if err != nil {
				t.Errorf("Resolve(%q) returned error: %v", uses, err)
			}
			if handler == nil {
				t.Errorf("Resolve(%q) returned nil handler", uses)
			}
		})
	}
}

func TestRegistry_ResolveUnknownAction(t *testing.T) {
	reg := NewRegistry("http://localhost:8080", "test-token")
	_, err := reg.Resolve("actions/unknown@v1")
	if err == nil {
		t.Error("expected error for unknown action, got nil")
	}
}
