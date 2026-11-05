package actions

import (
	"context"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/shell"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

// Downloads a real Node.js from nodejs.org and runs it. Needs the network, so it only runs when
// NODE_INTEGRATION=1 (for example inside the runner's Docker image, which is Alpine).
func TestSetupNode_realDownload(t *testing.T) {
	if os.Getenv("NODE_INTEGRATION") != "1" {
		t.Skip("set NODE_INTEGRATION=1 to download a real Node.js")
	}
	t.Setenv("RUNNER_TOOL_CACHE", t.TempDir())
	jctx := jobctx.New(nil, nil)
	streamer := runnerlog.New("step", nil, func(protocol.OutboundMessage) {}, zap.NewNop())

	out, err := NewSetupNodeAction().Execute(context.Background(), map[string]string{"node-version": "24"}, jctx, streamer, t.TempDir())
	require.NoError(t, err)
	assert.Regexp(t, `^v24\.`, out["node-version"])

	// The steps a Node workflow runs next: corepack, then pnpm, with the node the action installed.
	conclusion, err := shell.Run(context.Background(),
		"corepack enable && corepack prepare pnpm@10 --activate && pnpm --version", t.TempDir(), jctx, streamer)
	require.NoError(t, err)
	assert.Equal(t, "success", conclusion)
}
