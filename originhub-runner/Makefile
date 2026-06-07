# editorconfig-checker-disable-file
BINARY  = originhub-runner
VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo dev)
LDFLAGS = -s -w -X main.version=$(VERSION)
OUT     = dist

# Use public Go proxy when the local OriginHub proxy is unavailable.
# Override by setting GOPROXY in the shell or go env.
PUBLIC_PROXY ?= https://proxy.golang.org,direct

GOLANGCI_LINT = $(shell go env GOPATH)/bin/golangci-lint

.PHONY: build build-all build-linux-amd64 build-linux-arm64 build-macos-arm64 \
        build-windows-amd64 clean test lint lint-fix fmt fmt-check tidy

# ── Build ─────────────────────────────────────────────────────────────────────

build:
	go build -ldflags="$(LDFLAGS)" -o $(OUT)/$(BINARY) ./cmd/runner

build-linux-amd64:
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
	  go build -ldflags="$(LDFLAGS)" -o $(OUT)/$(BINARY)-linux-amd64 ./cmd/runner

build-linux-arm64:
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 \
	  go build -ldflags="$(LDFLAGS)" -o $(OUT)/$(BINARY)-linux-arm64 ./cmd/runner

build-macos-arm64:
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 \
	  go build -ldflags="$(LDFLAGS)" -o $(OUT)/$(BINARY)-darwin-arm64 ./cmd/runner

build-windows-amd64:
	CGO_ENABLED=0 GOOS=windows GOARCH=amd64 \
	  go build -ldflags="$(LDFLAGS)" -o $(OUT)/$(BINARY)-windows-amd64.exe ./cmd/runner

build-all: build-linux-amd64 build-linux-arm64 build-macos-arm64 build-windows-amd64

# ── Test ──────────────────────────────────────────────────────────────────────

test:
	go test -race -count=1 ./...

# ── Lint ──────────────────────────────────────────────────────────────────────

$(GOLANGCI_LINT):
	GOPROXY=$(PUBLIC_PROXY) go install github.com/golangci/golangci-lint/cmd/golangci-lint@v1.64.8

lint: $(GOLANGCI_LINT)
	$(GOLANGCI_LINT) run ./...

lint-fix: $(GOLANGCI_LINT)
	$(GOLANGCI_LINT) run --fix ./...

# ── Format ────────────────────────────────────────────────────────────────────

fmt:
	gofmt -w -s ./..
	goimports -local github.com/nuricanozturk/originhub-runner -w ./..

fmt-check:
	@out=$$(gofmt -l ./..); \
	if [ -n "$$out" ]; then echo "Unformatted files:\n$$out"; exit 1; fi

# ── Module hygiene ────────────────────────────────────────────────────────────

tidy:
	GOPROXY=$(PUBLIC_PROXY) go mod tidy

# ── Misc ──────────────────────────────────────────────────────────────────────

clean:
	rm -rf $(OUT)
