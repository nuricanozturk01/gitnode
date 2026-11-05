// Package proc makes child processes stop when their context is cancelled, together with
// everything they started.
package proc

import (
	"os/exec"
	"time"
)

// waitDelay bounds how long Wait lingers on the output pipes after the process was killed. A
// grandchild that survived (for example a detached docker CLI) would otherwise keep the pipes open
// and block the step forever.
const waitDelay = 5 * time.Second

// Configure makes cmd, on context cancellation, kill its whole process tree where the platform
// allows it, and stops waiting for inherited pipes shortly after.
func Configure(cmd *exec.Cmd) {
	configure(cmd)
	cmd.WaitDelay = waitDelay
}
