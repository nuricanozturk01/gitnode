package executor

import (
	"context"
	"os/exec"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

func identity(s string) string { return s }

func TestSplitOptions(t *testing.T) {
	cases := map[string][]string{
		"":         nil,
		"   ":      nil,
		"--cpus 1": {"--cpus", "1"},
		`--health-cmd "pg_isready -U postgres" --health-retries 5`: {"--health-cmd", "pg_isready -U postgres", "--health-retries", "5"},
		`--health-cmd 'redis-cli ping'`:                            {"--health-cmd", "redis-cli ping"},
		"--a\n--b":                                                 {"--a", "--b"},
		`--empty ""`:                                               {"--empty", ""},
	}
	for in, want := range cases {
		assert.Equal(t, want, splitOptions(in), in)
	}
}

func TestServiceRunArgs(t *testing.T) {
	svc := protocol.ServicePayload{
		Name:    "db",
		Image:   "postgres:16",
		Env:     map[string]string{"POSTGRES_PASSWORD": "${{ secrets.PW }}", "A": "b"},
		Ports:   []string{"5432:5432", "9000"},
		Volumes: []string{"data:/var/lib/postgresql/data"},
		Options: "--health-cmd 'pg_isready' --cpus 1",
	}
	got := serviceRunArgs("gitnode-1-svc-db", "job-1", "gitnode-1", svc, func(s string) string {
		return strings.ReplaceAll(s, "${{ secrets.PW }}", "secret")
	})
	assert.Equal(t, []string{
		"run", "-d", "--name", "gitnode-1-svc-db", "--network", "gitnode-1", "--network-alias", "db",
		"--label", "gitnode.job=job-1",
		"-e", "A=b", "-e", "POSTGRES_PASSWORD=secret",
		"-p", "5432:5432", "-p", "9000",
		"-v", "data:/var/lib/postgresql/data",
		"--health-cmd", "pg_isready", "--cpus", "1",
		"postgres:16",
	}, got)
}

func TestJobContainerRunArgsMountWorkspaceAtTheSamePath(t *testing.T) {
	spec := protocol.ContainerPayload{Image: "node:20", Env: map[string]string{"X": "1"}, Options: "--user 1000"}
	got := jobContainerRunArgs("gitnode-1-job", "job-1", "gitnode-1", spec, "/work/run/job", "/tmp/rt", identity)
	joined := strings.Join(got, " ")
	assert.Contains(t, joined, "-v /work/run/job:/work/run/job")
	assert.Contains(t, joined, "-v /tmp/rt:/tmp/rt")
	assert.Contains(t, joined, "-w /work/run/job")
	assert.Contains(t, joined, "--entrypoint tail")
	assert.Contains(t, joined, "--user 1000")
	assert.Equal(t, []string{"node:20", "-f", "/dev/null"}, got[len(got)-3:], "image, then the tail arguments")
}

func TestExecArgsPassEnvButNotThePath(t *testing.T) {
	c := &jobContainers{jobName: "job-ctr"}
	got := c.execArgs("/work", map[string]string{"B": "2", "A": "1", "PATH": "/host/bin"}, []string{"sh", "-e", "-c", "echo hi"})
	assert.Equal(t, []string{
		"exec", "-i", "-w", "/work", "-e", "A=1", "-e", "B=2", "job-ctr", "sh", "-e", "-c", "echo hi",
	}, got)
}

func TestPortHelpers(t *testing.T) {
	assert.Equal(t, "5432", containerPortOf("5432"))
	assert.Equal(t, "80", containerPortOf("8080:80"))
	assert.Equal(t, "80/tcp", containerPortOf("127.0.0.1:8080:80/tcp"))
	assert.Equal(t, "49153", hostPortOf("0.0.0.0:49153\n[::]:49153"))
}

func TestContainerShellArgsFallBackToShWithoutBash(t *testing.T) {
	bash := []string{"bash", "--noprofile", "--norc", "-eo", "pipefail", "-c", "echo hi"}
	assert.Equal(t, bash, containerShellArgs(bash, true))
	assert.Equal(t, []string{"sh", "-e", "-c", "echo hi"}, containerShellArgs(bash, false))
	assert.Equal(t, []string{"bash", "-e", "-c", "x"}, containerShellArgs([]string{"bash", "-e", "-c", "x"}, true))
	assert.Equal(t, []string{"sh", "-e", "-c", "x"}, containerShellArgs([]string{"bash", "-e", "-c", "x"}, false))
	assert.Equal(t, []string{"python3", "-c", "x"}, containerShellArgs([]string{"python3", "-c", "x"}, false))
}

func TestNeedsContainers(t *testing.T) {
	assert.False(t, needsContainers(protocol.JobPayload{}))
	assert.True(t, needsContainers(protocol.JobPayload{Services: []protocol.ServicePayload{{Name: "a"}}}))
	assert.True(t, needsContainers(protocol.JobPayload{Container: &protocol.ContainerPayload{Image: "alpine"}}))
	assert.False(t, needsContainers(protocol.JobPayload{Container: &protocol.ContainerPayload{}}))
}

func TestSanitizeNameAndShortID(t *testing.T) {
	assert.Equal(t, "my-svc_1.x", sanitizeName("my svc_1.x"))
	assert.Equal(t, "abcdef12", shortID("abcdef12-3456-7890"))
	assert.Equal(t, "abc", shortID("abc"))
}

// dockerWithImage skips a test unless Docker works and has the image locally (tests never pull).
func dockerWithImage(t *testing.T, image string) {
	t.Helper()
	docker, err := exec.LookPath("docker")
	if err != nil {
		t.Skip("docker is not installed")
	}
	if err := exec.CommandContext(context.Background(), docker, "image", "inspect", image).Run(); err != nil { //nolint:gosec
		t.Skipf("image %s is not available locally", image)
	}
}

const redisImage = "redis:7-alpine"

func redisService() protocol.ServicePayload {
	return protocol.ServicePayload{
		Name: "cache", Image: redisImage, Ports: []string{"6379"},
		Options: "--health-cmd 'redis-cli ping' --health-interval 1s --health-retries 30",
	}
}

func TestJob_serviceIsReachableFromHostSteps(t *testing.T) {
	dockerWithImage(t, redisImage)
	check := step("ping", `exec 3<>/dev/tcp/127.0.0.1/${{ job.services.cache.ports['6379'] }}
printf 'PING\r\n' >&3
head -n1 <&3`)
	check.Shell = "bash"
	o := runJob(t, protocol.JobPayload{
		ID:       "svc-host-job",
		Services: []protocol.ServicePayload{redisService()},
		Steps:    []protocol.StepPayload{check},
	})
	assert.Equal(t, "success", o.conclusion, o.log("ping"))
	assert.Contains(t, o.log("ping"), "+PONG")
}

func TestJob_containerStepsRunInsideAndReachServicesByName(t *testing.T) {
	dockerWithImage(t, redisImage)
	inside := step("inside", "cat /etc/alpine-release && redis-cli -h cache ping")
	write := step("write", "echo made-in-container > from-container.txt")
	read := step("read", "cat from-container.txt")
	o := runJob(t, protocol.JobPayload{
		ID:        "ctr-inside-job",
		Container: &protocol.ContainerPayload{Image: redisImage},
		Services:  []protocol.ServicePayload{redisService()},
		Steps:     []protocol.StepPayload{inside, write, read},
	})
	assert.Equal(t, "success", o.conclusion, o.log("inside"))
	assert.Contains(t, o.log("inside"), "PONG", "the service is reachable by its name")
	assert.Contains(t, o.log("read"), "made-in-container", "the workspace is shared between steps")
}

func TestJob_containerEnvAndGithubEnvFileReachTheContainer(t *testing.T) {
	dockerWithImage(t, redisImage)
	setEnv := step("set", `echo "FROM_FILE=yes" >> "$GITHUB_ENV"`)
	useEnv := step("use", `test "$FROM_FILE" = "yes" && test "$FROM_JOB" = "job-env" && echo ok`)
	o := runJob(t, protocol.JobPayload{
		ID:        "ctr-env-job",
		Env:       map[string]string{"FROM_JOB": "job-env"},
		Container: &protocol.ContainerPayload{Image: redisImage},
		Steps:     []protocol.StepPayload{setEnv, useEnv},
	})
	assert.Equal(t, "success", o.conclusion, o.log("use"))
}

func TestJob_unreachableImageFailsTheJobWithAReason(t *testing.T) {
	docker, err := exec.LookPath("docker")
	if err != nil {
		t.Skip("docker is not installed")
	}
	require.NoError(t, exec.CommandContext(context.Background(), docker, "version").Run()) //nolint:gosec

	o := runJob(t, protocol.JobPayload{
		ID:        "ctr-bad-image-job",
		Container: &protocol.ContainerPayload{Image: "gitnode.invalid/no/such-image:0"},
		Steps:     []protocol.StepPayload{step("first", "echo never")},
	})
	assert.Equal(t, "failure", o.conclusion)
	assert.Equal(t, "failure", o.steps["first"].conclusion)
	assert.Contains(t, o.log("first"), "Initialize containers failed")
	assert.NotContains(t, o.log("first"), "never")
}

func TestWithContainerIgnoresAJobWithoutJobContainer(t *testing.T) {
	assert.Nil(t, NewShellExecutor("/w").WithContainer(nil).container)
	assert.Nil(t, NewShellExecutor("/w").WithContainer(&jobContainers{}).container, "services only")
	c := &jobContainers{jobName: "ctr"}
	assert.Same(t, c, NewShellExecutor("/w").WithContainer(c).container)
}
