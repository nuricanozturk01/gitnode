# originhub-runner

OriginHub CI/CD runner agent. Connects to the server over WebSocket, receives jobs, and executes them. No JRE required — single static binary (~12 MB).

---

## Requirements

| Tool | Version |
|------|---------|
| Go   | 1.24+ |
| Docker *(optional)* | 20+ |

---

## Quick Start

### 1. Build the binary

```bash
make build                  # local architecture → dist/originhub-runner
make build-linux-amd64      # Linux x86-64
make build-linux-arm64      # Linux ARM64
make build-macos-arm64      # macOS Apple Silicon
make build-windows-amd64    # Windows x86-64
make build-all              # all platforms
```

### 2. Register and start the runner

```bash
./dist/originhub-runner start \
  --server-url http://originhub.company.com \
  --token ghrt_xxxxxxxxxxxx \
  --name my-runner \
  --labels self-hosted,linux,docker \
  --executor docker \
  --work-dir /tmp/originhub-runner \
  --concurrent-jobs 2
```

### 3. Start with a config file

```bash
cat > ~/.originhub-runner/config.yml << EOF
server_url: http://originhub.company.com
token: ghrt_xxxxxxxxxxxx     # for initial registration; runner_token is used afterward
name: my-runner
labels:
  - self-hosted
  - linux
  - docker
executor: docker             # shell or docker
work_dir: /tmp/originhub-runner
concurrent_jobs: 2
EOF

./dist/originhub-runner start --config ~/.originhub-runner/config.yml
```

After successful registration, `runner_id` and `runner_token` are written to the config file automatically.

---

## Commands

| Command | Description |
|---------|-------------|
| `make build` | Build binary for the local platform |
| `make build-all` | Build binaries for 4 platforms (`dist/`) |
| `make test` | Run all tests (`-race -count=1`) |
| `make lint` | Static analysis with golangci-lint |
| `make lint-fix` | Fix auto-fixable lint issues |
| `make fmt` | Format with `gofmt + goimports` |
| `make fmt-check` | Check formatting (for CI) |
| `make tidy` | `go mod tidy` — via public proxy |
| `make clean` | Remove the `dist/` directory |

---

## Linter and Formatter

### Formatter

The project uses **gofmt** and **goimports**.

```bash
make fmt          # format all files
make fmt-check    # fail if any file is unformatted (for CI)
```

Import order:

1. Standard library
2. Third-party packages
3. `github.com/nuricanozturk/originhub-runner/...` (internal)

### Linter

**golangci-lint v2** — configuration: `.golangci.yml`

```bash
make lint         # run all rules
make lint-fix     # fix auto-fixable issues
```

Active linters: `errcheck`, `govet` (including shadow), `staticcheck`, `unused`, `bodyclose`, `copyloopvar`, `durationcheck`, `errorlint`, `gocritic`, `misspell`, `noctx`, `revive`, `unconvert`, `unparam`, `gosec`.

Intentionally suppressed rules:

| Rule | Reason |
|------|--------|
| `gosec G204` | Expected `bash -c` usage in the executor |
| `gosec G304` | Config `Load()` accepts a user-provided path — expected |
| `gosec G306` | Workspace directory permissions are sufficient |
| `revive/exported` | Godoc requirement on internal packages adds unnecessary noise |

---

## GOPROXY

Go module proxy settings live in `~/.config/go/env` (or `$GOENV`).

Recommended configuration — OriginHub proxy first, public proxy as fallback:

```
GOPROXY=http://<user>:<pass>@<originhub-host>/go,https://proxy.golang.org,direct
```

The `make tidy` and `make lint` commands also use the public proxy via the `PUBLIC_PROXY` variable:

```bash
# Temporarily use the public proxy
make tidy PUBLIC_PROXY=https://proxy.golang.org,direct
```

---

## Project Structure

```
originhub-runner/
├── cmd/runner/main.go          # CLI entry point (cobra)
├── internal/
│   ├── actions/                # Built-in action implementations
│   │   ├── registry.go         # ActionHandler interface + Registry
│   │   └── checkout.go         # actions/checkout@v1
│   ├── config/config.go        # Config struct (YAML load/save)
│   ├── connection/ws_client.go # WebSocket client (auto-reconnect)
│   ├── context/job_context.go  # Job env, secrets, step outputs
│   ├── executor/
│   │   ├── job_executor.go     # Step orchestration (goroutine-per-job)
│   │   ├── shell_executor.go   # os/exec based
│   │   └── docker_executor.go  # docker/docker SDK
│   ├── log/streamer.go         # Log line → WS message (secret masking)
│   ├── registration/service.go # POST /api/actions/runners/register
│   └── workspace/manager.go   # Per-job temp directory create/delete
└── pkg/protocol/messages.go    # WS message types and payload structs
```
