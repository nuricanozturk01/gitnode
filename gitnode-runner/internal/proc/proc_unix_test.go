//go:build !windows

package proc

import (
	"bytes"
	"context"
	"os/exec"
	"testing"
	"time"
)

// A grandchild that keeps the output pipe open must not keep Run waiting after cancel.
func TestConfigureKillsProcessTree(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cmd := exec.CommandContext(ctx, "bash", "-c", "sleep 60 & sleep 60")
	var out bytes.Buffer
	cmd.Stdout = &out
	Configure(cmd)

	done := make(chan error, 1)
	go func() { done <- cmd.Run() }()

	time.Sleep(300 * time.Millisecond)
	cancel()

	select {
	case <-done:
	case <-time.After(3 * time.Second):
		t.Fatal("Run did not return after cancel; child processes were not killed")
	}
}
