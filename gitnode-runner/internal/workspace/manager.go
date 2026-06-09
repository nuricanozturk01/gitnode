package workspace

import (
	"fmt"
	"os"
	"path/filepath"
)

// Manager creates and removes per-job workspace directories.
type Manager struct {
	baseDir string
}

// New returns a Manager rooted at baseDir.
func New(baseDir string) *Manager {
	return &Manager{baseDir: baseDir}
}

// Create makes a temp directory at <baseDir>/runs/<runID>/<jobID>/.
func (m *Manager) Create(runID, jobID string) (string, error) {
	dir := filepath.Join(m.baseDir, "runs", runID, jobID)
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return "", fmt.Errorf("create workspace %s: %w", dir, err)
	}
	return dir, nil
}

// Remove deletes the workspace directory and its contents.
func (m *Manager) Remove(runID, jobID string) error {
	dir := filepath.Join(m.baseDir, "runs", runID, jobID)
	return os.RemoveAll(dir)
}
