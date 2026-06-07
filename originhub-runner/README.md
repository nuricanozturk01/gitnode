# originhub-runner

Go CI/CD agent — WebSocket jobs, shell/docker executor.

**Optional** — only for Actions workflows.

## Run

| Command | Runs |
|---------|------|
| `make runner-build` | Binary → `dist/originhub-runner` |
| `./dist/originhub-runner start --server-url http://localhost:8080 --token ghrt_...` | Connect to server |

Registration token: repo → Settings → Actions → Runners

## Dev

| Command | Runs |
|---------|------|
| `make build` | Local arch binary |
| `make test` | Go tests |
| `make lint` | golangci-lint |

See [CONTRIBUTING.md](../CONTRIBUTING.md#runner)
