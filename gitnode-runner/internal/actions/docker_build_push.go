package actions

import (
	"context"
	"fmt"
	"strings"

	jobctx "github.com/nuricanozturk/gitnode-runner/internal/context"
	runnerlog "github.com/nuricanozturk/gitnode-runner/internal/log"
	"github.com/nuricanozturk/gitnode-runner/internal/shell"
)

// DockerBuildPushAction implements docker/build-push-action@v1 through v6.
// Builds a Docker image using `docker buildx build` and optionally pushes it.
type DockerBuildPushAction struct{}

func NewDockerBuildPushAction() *DockerBuildPushAction { return &DockerBuildPushAction{} }

func (a *DockerBuildPushAction) Name() string { return "docker/build-push-action" }
func (a *DockerBuildPushAction) Versions() []string {
	return []string{"v1", "v2", "v3", "v4", "v5", "v6"}
}

func (a *DockerBuildPushAction) Execute(
	ctx context.Context,
	inputs map[string]string,
	jctx *jobctx.JobContext,
	streamer *runnerlog.Streamer,
	workDir string,
) (map[string]string, error) {

	buildCtx := inputs["context"]
	if buildCtx == "" {
		buildCtx = "."
	}

	tags := splitLines(inputs["tags"])
	if len(tags) == 0 {
		return nil, fmt.Errorf("docker/build-push-action: at least one tag is required")
	}

	opts := buildOpts{
		file:      inputs["file"],
		push:      inputs["push"] == "true",
		load:      inputs["load"] == "true",
		noCache:   inputs["no-cache"] == "true",
		pull:      inputs["pull"] == "true",
		target:    inputs["target"],
		platforms: inputs["platforms"],
		tags:      tags,
		buildArgs: splitLines(inputs["build-args"]),
		labels:    splitLines(inputs["labels"]),
		secrets:   splitLines(inputs["secrets"]),
		outputs:   splitLines(inputs["outputs"]),
		ssh:       inputs["ssh"],
		cacheFrom: filterGHACache(splitLines(inputs["cache-from"]), streamer),
		cacheTo:   filterGHACache(splitLines(inputs["cache-to"]), streamer),
	}

	cmd := buildBuildxCommand(buildCtx, opts)

	streamer.Emit(fmt.Sprintf("Building image: %s", strings.Join(tags, ", ")), "info")
	if opts.platforms != "" {
		streamer.Emit(fmt.Sprintf("Platforms: %s", opts.platforms), "info")
	}

	conclusion, err := shell.Run(ctx, cmd, workDir, jctx, streamer)
	if err != nil || conclusion == "failure" {
		return nil, fmt.Errorf("docker/build-push-action: build failed")
	}

	if opts.push {
		streamer.Emit(fmt.Sprintf("Pushed: %s", strings.Join(tags, ", ")), "info")
	}
	return map[string]string{}, nil
}

type buildOpts struct {
	file      string
	push      bool
	load      bool
	noCache   bool
	pull      bool
	target    string
	platforms string
	ssh       string
	tags      []string
	buildArgs []string
	labels    []string
	secrets   []string
	outputs   []string
	cacheFrom []string
	cacheTo   []string
}

func buildBuildxCommand(buildCtx string, o buildOpts) string {
	var b strings.Builder
	b.WriteString("docker buildx build")

	if o.file != "" {
		fmt.Fprintf(&b, " --file %q", o.file)
	}
	if o.platforms != "" {
		fmt.Fprintf(&b, " --platform %s", o.platforms)
	}
	if o.push {
		b.WriteString(" --push")
	}
	if o.load {
		b.WriteString(" --load")
	}
	if o.noCache {
		b.WriteString(" --no-cache")
	}
	if o.pull {
		b.WriteString(" --pull")
	}
	if o.target != "" {
		fmt.Fprintf(&b, " --target %q", o.target)
	}
	if o.ssh != "" {
		fmt.Fprintf(&b, " --ssh %q", o.ssh)
	}
	for _, tag := range o.tags {
		fmt.Fprintf(&b, " --tag %q", tag)
	}
	for _, arg := range o.buildArgs {
		fmt.Fprintf(&b, " --build-arg %q", arg)
	}
	for _, label := range o.labels {
		fmt.Fprintf(&b, " --label %q", label)
	}
	for _, secret := range o.secrets {
		fmt.Fprintf(&b, " --secret %q", secret)
	}
	for _, out := range o.outputs {
		fmt.Fprintf(&b, " --output %q", out)
	}
	for _, cf := range o.cacheFrom {
		fmt.Fprintf(&b, " --cache-from %q", cf)
	}
	for _, ct := range o.cacheTo {
		fmt.Fprintf(&b, " --cache-to %q", ct)
	}

	fmt.Fprintf(&b, " %s", buildCtx)
	return b.String()
}

// splitLines splits a newline- or comma-separated string into trimmed non-empty entries.
func splitLines(s string) []string {
	var out []string
	for _, part := range strings.FieldsFunc(s, func(r rune) bool { return r == '\n' || r == ',' }) {
		if v := strings.TrimSpace(part); v != "" {
			out = append(out, v)
		}
	}
	return out
}

// filterGHACache drops cache entries of type=gha (GitHub Actions cache, unsupported here).
func filterGHACache(args []string, streamer *runnerlog.Streamer) []string {
	var out []string
	for _, arg := range args {
		if strings.HasPrefix(strings.TrimSpace(arg), "type=gha") {
			streamer.Emit("Warning: cache type=gha is not supported on GitNode runners — skipping", "warning")
			continue
		}
		out = append(out, arg)
	}
	return out
}
