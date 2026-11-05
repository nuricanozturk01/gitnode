# gitnode-runner

Go CI/CD agent — WebSocket jobs, shell/docker executor.

**Optional** — only for Actions workflows.

## Run

| Command | Runs |
|---------|------|
| `make runner-build` | Binary → `dist/gitnode-runner` |
| `./dist/gitnode-runner start --server-url http://localhost:8080 --token ghrt_...` | Connect to server |

Registration token: web UI → **Settings → Actions → Self-hosted runners** → generate a token.

## Dev

| Command | Runs |
|---------|------|
| `make build` | Local arch binary |
| `make test` | Go tests |
| `make lint` | golangci-lint |

See [CONTRIBUTING.md](../CONTRIBUTING.md#runner)
