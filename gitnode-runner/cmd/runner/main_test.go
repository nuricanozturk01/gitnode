package main

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/nuricanozturk/gitnode-runner/internal/config"
)

func registeredConfig(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.yaml")
	require.NoError(t, (&config.Config{
		ServerURL: "https://example.test", Token: "old-token", Name: "r",
		RunnerID: "runner-1", RunnerToken: "runner-secret",
	}).Save(path))
	return path
}

func TestBuildConfig_sameTokenKeepsTheRegistration(t *testing.T) {
	cfg, err := buildConfig(registeredConfig(t), "", "old-token", "", "", "", "", 0)
	require.NoError(t, err)
	assert.True(t, cfg.Registered())
	assert.Equal(t, "runner-1", cfg.RunnerID)
}

func TestBuildConfig_noTokenKeepsTheRegistration(t *testing.T) {
	cfg, err := buildConfig(registeredConfig(t), "", "", "", "", "", "", 0)
	require.NoError(t, err)
	assert.True(t, cfg.Registered())
}

func TestBuildConfig_newTokenRegistersAgain(t *testing.T) {
	cfg, err := buildConfig(registeredConfig(t), "", "new-token", "", "", "", "", 0)
	require.NoError(t, err)
	assert.False(t, cfg.Registered(), "a new token must not reuse the old identity")
	assert.Equal(t, "new-token", cfg.Token)
}
