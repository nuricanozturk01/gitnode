package actions

import (
	"testing"
)

func TestRegistry_ResolveKnownActions(t *testing.T) {
	reg := NewRegistry("http://localhost:8080", "test-token")

	known := []string{
		// checkout
		"actions/checkout@v1",
		"actions/checkout@v2",
		"actions/checkout@v3",
		"actions/checkout@v4",
		// setup-node
		"actions/setup-node@v1",
		"actions/setup-node@v2",
		"actions/setup-node@v3",
		"actions/setup-node@v4",
		// setup-java
		"actions/setup-java@v1",
		"actions/setup-java@v2",
		"actions/setup-java@v3",
		"actions/setup-java@v4",
		// setup-go
		"actions/setup-go@v1",
		"actions/setup-go@v2",
		"actions/setup-go@v3",
		"actions/setup-go@v4",
		"actions/setup-go@v5",
		// setup-python
		"actions/setup-python@v1",
		"actions/setup-python@v2",
		"actions/setup-python@v3",
		"actions/setup-python@v4",
		"actions/setup-python@v5",
		// pnpm
		"pnpm/action-setup@v1",
		"pnpm/action-setup@v2",
		"pnpm/action-setup@v3",
		"pnpm/action-setup@v4",
		// docker login
		"docker/login-action@v1",
		"docker/login-action@v2",
		"docker/login-action@v3",
		// docker buildx
		"docker/setup-buildx-action@v1",
		"docker/setup-buildx-action@v2",
		"docker/setup-buildx-action@v3",
		// docker build-push
		"docker/build-push-action@v1",
		"docker/build-push-action@v2",
		"docker/build-push-action@v3",
		"docker/build-push-action@v4",
		"docker/build-push-action@v5",
		"docker/build-push-action@v6",
		// cache / artifacts
		"actions/cache@v1",
		"actions/cache@v2",
		"actions/cache@v3",
		"actions/cache@v4",
		"actions/upload-artifact@v1",
		"actions/upload-artifact@v2",
		"actions/upload-artifact@v3",
		"actions/upload-artifact@v4",
		"actions/download-artifact@v1",
		"actions/download-artifact@v2",
		"actions/download-artifact@v3",
		"actions/download-artifact@v4",
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
