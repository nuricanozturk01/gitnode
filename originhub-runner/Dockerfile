# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM golang:1.24-alpine AS build

RUN apk add --no-cache git make

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o dist/originhub-runner ./cmd/runner

# ─── Stage 2: Final Image ─────────────────────────────────────────────────────
FROM alpine:3.21

RUN apk add --no-cache ca-certificates git bash docker-cli

RUN addgroup -g 1001 -S runner && adduser -u 1001 -S runner -G runner

WORKDIR /app
COPY --from=build /app/dist/originhub-runner ./originhub-runner

RUN mkdir -p /tmp/originhub-runner && chown runner:runner /tmp/originhub-runner

USER runner

ENTRYPOINT ["./originhub-runner", "start"]
