# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM golang:1.24-alpine AS build

RUN apk add --no-cache git make

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o dist/gitnode-runner ./cmd/runner

# ─── Stage 2: Final Image ─────────────────────────────────────────────────────
# Debian (glibc), not Alpine (musl): the prebuilt toolchains that setup-node, setup-go, setup-python
# and friends download are built for glibc, as on GitHub's ubuntu images.
FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

# What workflows written for GitHub's ubuntu images take for granted. Running as root, so
# `sudo apt-get install ...` in a workflow works too.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates tzdata sudo bash \
        git git-lfs openssh-client \
        curl wget gnupg openssl \
        jq zip unzip xz-utils bzip2 tar gzip patch rsync file procps \
        build-essential pkg-config \
        python3 python3-pip python3-venv \
    && git lfs install --system \
    && rm -rf /var/lib/apt/lists/*

# Docker client, buildx and compose: static binaries, the daemon is the host's (mount its socket).
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker/buildx-bin:latest /buildx /usr/libexec/docker/cli-plugins/docker-buildx
COPY --from=docker/compose-bin:latest /docker-compose /usr/libexec/docker/cli-plugins/docker-compose

WORKDIR /app
COPY --from=build /app/dist/gitnode-runner ./gitnode-runner

# Runs as root so it can use the host Docker socket (-v /var/run/docker.sock:...).
# /data keeps config.yaml so the one-time token is only used on first start.
RUN mkdir -p /data /tmp/gitnode-runner
VOLUME /data

ENTRYPOINT ["./gitnode-runner", "start", "--config", "/data/config.yaml"]
