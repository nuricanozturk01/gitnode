# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM golang:1.24-alpine AS build

RUN apk add --no-cache git make

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o dist/gitnode-runner ./cmd/runner

# ─── Stage 2: Final Image ─────────────────────────────────────────────────────
FROM alpine:3.21

# libstdc++ is needed by the Node.js musl builds that actions/setup-node downloads.
RUN apk add --no-cache ca-certificates git bash docker-cli docker-cli-buildx libstdc++

WORKDIR /app
COPY --from=build /app/dist/gitnode-runner ./gitnode-runner

# Runs as root so it can use the host Docker socket (-v /var/run/docker.sock:...).
# /data keeps config.yaml so the one-time token is only used on first start.
RUN mkdir -p /data /tmp/gitnode-runner
VOLUME /data

ENTRYPOINT ["./gitnode-runner", "start", "--config", "/data/config.yaml"]
