package executor

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"sort"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

const (
	healthTimeout  = 2 * time.Minute
	healthInterval = 2 * time.Second
	cleanupTimeout = time.Minute
)

// jobContainers is the Docker side of a job: a network, the `services:` containers and, for a job
// with `container:`, the job container its run steps execute in. The workspace and the runtime
// directory are mounted at the same paths as on the host, so paths in env files and scripts mean
// the same inside and outside the container.
type jobContainers struct {
	docker   string
	network  string
	prefix   string
	started  []string
	jobName  string // the job container; empty when steps run on the host
	hasBash  bool
	uid, gid int
	log      *zap.Logger
	released sync.Once
	// ports maps service name → container port → published host port.
	ports map[string]map[string]string
}

func (c *jobContainers) run(ctx context.Context, args ...string) (string, error) {
	out, err := exec.CommandContext(ctx, c.docker, args...).CombinedOutput() //nolint:gosec
	text := strings.TrimSpace(string(out))
	if err != nil {
		return text, fmt.Errorf("docker %s: %w: %s", firstWord(args), err, lastLine(text))
	}
	return text, nil
}

// needsContainers is true when the job asks for Docker: services or a job container.
func needsContainers(job protocol.JobPayload) bool {
	return len(job.Services) > 0 || (job.Container != nil && job.Container.Image != "")
}

// startJobContainers brings up the network, the services and the job container. emit reports what
// is happening to the first step's log. On error everything already started is removed.
func startJobContainers(
	ctx context.Context,
	job protocol.JobPayload,
	workDir, runtimeDir string,
	interpolate func(string) string,
	emit func(string),
	log *zap.Logger,
) (*jobContainers, error) {

	docker, err := exec.LookPath("docker")
	if err != nil {
		return nil, errors.New("this job uses services or a container, which needs the docker CLI on the runner")
	}
	id := shortID(job.ID)
	c := &jobContainers{
		docker:  docker,
		network: "gitnode-" + id,
		prefix:  "gitnode-" + id,
		uid:     os.Getuid(),
		gid:     os.Getgid(),
		log:     log,
		ports:   map[string]map[string]string{},
	}
	if _, err := c.run(ctx, "network", "create", c.network); err != nil {
		return nil, err
	}

	for _, svc := range job.Services {
		emit(fmt.Sprintf("Starting service %s (%s)", svc.Name, svc.Image))
		if err := c.startService(ctx, id, svc, interpolate); err != nil {
			c.cleanup()
			return nil, err
		}
	}
	if job.Container != nil && job.Container.Image != "" {
		emit(fmt.Sprintf("Starting job container (%s)", job.Container.Image))
		if err := c.startJobContainer(ctx, id, *job.Container, workDir, runtimeDir, interpolate); err != nil {
			c.cleanup()
			return nil, err
		}
	}
	return c, nil
}

func (c *jobContainers) startService(
	ctx context.Context, id string, svc protocol.ServicePayload, interpolate func(string) string,
) error {
	name := c.prefix + "-svc-" + sanitizeName(svc.Name)
	args := serviceRunArgs(name, id, c.network, svc, interpolate)
	if _, err := c.run(ctx, args...); err != nil {
		return fmt.Errorf("service %q: %w", svc.Name, err)
	}
	c.started = append(c.started, name)

	for _, mapping := range svc.Ports {
		containerPort := containerPortOf(mapping)
		host, err := c.run(ctx, "port", name, containerPort)
		if err != nil {
			continue // a port the image does not publish
		}
		if c.ports[svc.Name] == nil {
			c.ports[svc.Name] = map[string]string{}
		}
		c.ports[svc.Name][strings.SplitN(containerPort, "/", 2)[0]] = hostPortOf(host)
	}
	if strings.Contains(svc.Options, "--health-cmd") {
		if err := c.waitHealthy(ctx, name); err != nil {
			return fmt.Errorf("service %q: %w", svc.Name, err)
		}
	}
	return nil
}

func (c *jobContainers) startJobContainer(
	ctx context.Context,
	id string,
	spec protocol.ContainerPayload,
	workDir, runtimeDir string,
	interpolate func(string) string,
) error {
	name := c.prefix + "-job"
	args := jobContainerRunArgs(name, id, c.network, spec, workDir, runtimeDir, interpolate)
	if _, err := c.run(ctx, args...); err != nil {
		return fmt.Errorf("job container: %w", err)
	}
	c.started = append(c.started, name)
	c.jobName = name
	_, bashErr := c.run(ctx, "exec", name, "sh", "-c", "command -v bash")
	c.hasBash = bashErr == nil
	return nil
}

// waitHealthy polls the health status Docker computes from --health-cmd.
func (c *jobContainers) waitHealthy(ctx context.Context, name string) error {
	deadline := time.Now().Add(healthTimeout)
	for {
		status, err := c.run(ctx, "inspect", "-f", "{{.State.Health.Status}}", name)
		switch {
		case err == nil && status == "healthy":
			return nil
		case err == nil && status == "unhealthy":
			return errors.New("the health check reports the service as unhealthy")
		case ctx.Err() != nil:
			return ctx.Err()
		case time.Now().After(deadline):
			return fmt.Errorf("not healthy after %s", healthTimeout)
		}
		time.Sleep(healthInterval)
	}
}

// release hands the workspace back and removes everything the job started. It is safe to call
// more than once; only the first call does the work.
func (c *jobContainers) release(workDir, runtimeDir string) {
	if c == nil {
		return
	}
	c.released.Do(func() {
		c.handBackFiles(context.Background(), workDir, runtimeDir)
		c.cleanup()
	})
}

// cleanup removes the containers and the network. It hands the workspace back to the runner's
// user first, since a container that ran as root may have created files there.
func (c *jobContainers) cleanup() {
	ctx, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()
	for i := len(c.started) - 1; i >= 0; i-- {
		if _, err := c.run(ctx, "rm", "-f", "-v", c.started[i]); err != nil {
			c.log.Warn("could not remove container", zap.String("name", c.started[i]), zap.Error(err))
		}
	}
	if _, err := c.run(ctx, "network", "rm", c.network); err != nil {
		c.log.Warn("could not remove network", zap.String("network", c.network), zap.Error(err))
	}
}

// handBackFiles makes the files the job container created owned by the runner's user.
func (c *jobContainers) handBackFiles(ctx context.Context, dirs ...string) {
	if c == nil || c.jobName == "" {
		return
	}
	args := append([]string{"exec", c.jobName, "chown", "-R", fmt.Sprintf("%d:%d", c.uid, c.gid)}, dirs...)
	if _, err := c.run(ctx, args...); err != nil {
		c.log.Warn("could not hand the workspace back", zap.Error(err))
	}
}

// ── argument builders (pure, so they can be tested without Docker) ─────────────────────────────

func serviceRunArgs(
	name, jobID, network string, svc protocol.ServicePayload, interpolate func(string) string,
) []string {
	args := []string{
		"run", "-d", "--name", name, "--network", network, "--network-alias", svc.Name,
		"--label", "gitnode.job=" + jobID,
	}
	args = append(args, envArgs(svc.Env, interpolate)...)
	for _, port := range svc.Ports {
		args = append(args, "-p", port)
	}
	for _, volume := range svc.Volumes {
		args = append(args, "-v", volume)
	}
	args = append(args, splitOptions(interpolate(svc.Options))...)
	return append(args, interpolate(svc.Image))
}

func jobContainerRunArgs(
	name, jobID, network string,
	spec protocol.ContainerPayload,
	workDir, runtimeDir string,
	interpolate func(string) string,
) []string {
	args := []string{
		"run", "-d", "--name", name, "--network", network, "--label", "gitnode.job=" + jobID,
		"-v", workDir + ":" + workDir,
		"-v", runtimeDir + ":" + runtimeDir,
		"-w", workDir,
		"--entrypoint", "tail",
	}
	args = append(args, envArgs(spec.Env, interpolate)...)
	for _, port := range spec.Ports {
		args = append(args, "-p", port)
	}
	for _, volume := range spec.Volumes {
		args = append(args, "-v", volume)
	}
	args = append(args, splitOptions(interpolate(spec.Options))...)
	return append(args, interpolate(spec.Image), "-f", "/dev/null")
}

// execArgs is the `docker exec` command line running argv in the job container at dir. PATH is
// the host's, so it is not passed on; the container keeps its own.
func (c *jobContainers) execArgs(dir string, env map[string]string, argv []string) []string {
	args := []string{"exec", "-i", "-w", dir}
	keys := make([]string, 0, len(env))
	for k := range env {
		if k != "PATH" {
			keys = append(keys, k)
		}
	}
	sort.Strings(keys)
	for _, k := range keys {
		args = append(args, "-e", k+"="+env[k])
	}
	args = append(args, c.jobName)
	return append(args, argv...)
}

func envArgs(env map[string]string, interpolate func(string) string) []string {
	keys := make([]string, 0, len(env))
	for k := range env {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var args []string
	for _, k := range keys {
		args = append(args, "-e", k+"="+interpolate(env[k]))
	}
	return args
}

// containerPortOf is the container side of a port mapping: "5432", "8080:80", "127.0.0.1:80:80/tcp".
func containerPortOf(mapping string) string {
	parts := strings.Split(mapping, ":")
	return parts[len(parts)-1]
}

// hostPortOf reads `docker port` output such as "0.0.0.0:49153" (first line) as the host port.
func hostPortOf(output string) string {
	line := strings.SplitN(output, "\n", 2)[0]
	return line[strings.LastIndex(line, ":")+1:]
}

// splitOptions splits docker run options like a shell would for plain flags: whitespace separated,
// single and double quotes group.
func splitOptions(options string) []string {
	var out []string
	var current strings.Builder
	var quote rune
	started := false
	for _, r := range options {
		switch {
		case quote != 0:
			if r == quote {
				quote = 0
			} else {
				current.WriteRune(r)
			}
		case r == '"' || r == '\'':
			quote, started = r, true
		case r == ' ' || r == '\t' || r == '\n':
			if started {
				out = append(out, current.String())
				current.Reset()
				started = false
			}
		default:
			current.WriteRune(r)
			started = true
		}
	}
	if started {
		out = append(out, current.String())
	}
	return out
}

func shortID(id string) string {
	id = strings.ReplaceAll(id, "-", "")
	if len(id) > 8 {
		return id[:8]
	}
	return id
}

func sanitizeName(name string) string {
	var b strings.Builder
	for _, r := range name {
		if r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '_' || r == '.' || r == '-' {
			b.WriteRune(r)
		} else {
			b.WriteRune('-')
		}
	}
	return b.String()
}

func firstWord(args []string) string {
	if len(args) == 0 {
		return ""
	}
	return args[0]
}

func lastLine(text string) string {
	lines := strings.Split(strings.TrimSpace(text), "\n")
	return lines[len(lines)-1]
}
