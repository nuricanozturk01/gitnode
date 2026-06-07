# OriginHub – Makefile
# Usage: make <target>

NETWORK        := originhub
POSTGRES_NAME  := originhub-postgres
REDIS_NAME     := originhub-redis
PROMETHEUS_NAME:= originhub-prometheus
GRAFANA_NAME   := originhub-grafana
APP_NAME       := originhub
RUNNER_NAME    := originhub-runner
RUNNER_DIR     := originhub-runner
LDAP_NAME      := originhub-ldap
LDAP_IMAGE     := ghcr.io/rroemhild/docker-test-openldap:master
LDAP_PORT      := 389
IMAGE          := repo.repsy.io/nuricanozturk/originhub/originhub-os:latest

POSTGRES_DB    := originhub
POSTGRES_USER  := admin
POSTGRES_PASS  := admin123
REDIS_PORT     := 6379

JWT_SECRET     := 995a44f7111b23ebed8ad37e8b9cbe380dd5022f8b3bf67b16c8e223456f74a0
GIT_REPO_ROOT  := /data/repos
REPOS_VOLUME   := originhub-repos
POSTGRES_LOGS_VOLUME := originhub-postgres-logs
SPRING_PROFILE := os

HTTP_PORT  := 8080
SSH_PORT   := 2222

# OAuth2 – fill in before running
GOOGLE_CLIENT_ID     := YOUR_CLIENT
GOOGLE_CLIENT_SECRET := YOUR_SECRET
GITHUB_CLIENT_ID     := YOUR_CLIENT
GITHUB_CLIENT_SECRET := YOUR_SECRET
GITLAB_CLIENT_ID     := YOUR_CLIENT
GITLAB_CLIENT_SECRET := YOUR_SECRET

ADMIN_ENABLED  ?= true

# ──────────────────────────────────────────────────────────────────────────────
.PHONY: all up down start stop restart \
  infra infra-down infra-stop infra-start \
  monitoring monitoring-down \
  app app-stop \
  runner-build runner-build-all \
  ldap-up ldap-down \
  dev-setup dev-backend \
  actions-encryption-key saml-keygen \
  test test-backend test-runner test-lint verify \
  k8s-bootstrap k8s-install k8s-template k8s-uninstall k8s-purge k8s-kubeconfig \
  logs logs-db logs-redis logs-prometheus logs-grafana \
  ps build purge help

LOCAL_CONFIG := originhub-backend/src/main/resources/application-local.yaml
LOCAL_CONFIG_EXAMPLE := originhub-backend/src/main/resources/application-local.yaml.example

all: up

# ── Full stack ────────────────────────────────────────────────────────────────

up: infra app
	@echo ""
	@echo "  OriginHub stack is up"
	@echo "  App         → http://localhost:$(HTTP_PORT)"
	@echo "  SSH         → localhost:$(SSH_PORT)"
	@echo "  Admin API   → $(if $(filter false,$(ADMIN_ENABLED)),disabled — ADMIN_ENABLED=false,enabled (default))"
	@echo "  Monitoring  → optional: make monitoring"
	@echo ""

down: app-stop infra-down
	@echo "All containers stopped and removed."

start: infra-start
	docker start $(APP_NAME)

stop: app-stop infra-stop

restart: stop start

# ── Infrastructure (Postgres · Redis) ───────────────────────────────────────

infra:
	docker compose up -d --build
	@echo "Waiting for Postgres to be ready..."
	@until docker exec $(POSTGRES_NAME) pg_isready -U $(POSTGRES_USER) > /dev/null 2>&1; do sleep 1; done
	@echo "Postgres ready."

infra-stop:
	docker compose stop

infra-start:
	docker compose start

infra-down:
	docker compose down

# ── Observability (optional) ──────────────────────────────────────────────────

monitoring:
	docker compose --profile monitoring up -d prometheus grafana
	@echo ""
	@echo "  Monitoring stack is up"
	@echo "  Prometheus  → http://localhost:9090"
	@echo "  Grafana     → http://localhost:3000  (admin / admin)"
	@echo ""

monitoring-down:
	docker compose --profile monitoring stop prometheus grafana

# ── App container ─────────────────────────────────────────────────────────────

ACTIONS_ENCRYPTION_KEY ?= $(shell cat $(HOME)/.originhub/actions-encryption-key 2>/dev/null)

app:
	@docker ps -a --format "{{.Names}}" | grep -q "^$(APP_NAME)$$" \
		&& echo "$(APP_NAME) already exists – skipping." \
		|| docker run -d \
			--name $(APP_NAME) \
			--network $(NETWORK) \
			-p $(HTTP_PORT):8080 \
			-p $(SSH_PORT):2222 \
			-e JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=100" \
			-e SPRING_DATASOURCE_URL=jdbc:postgresql://$(POSTGRES_NAME):5432/$(POSTGRES_DB) \
			-e SPRING_DATASOURCE_USERNAME=$(POSTGRES_USER) \
			-e SPRING_DATASOURCE_PASSWORD=$(POSTGRES_PASS) \
			-e ORIGINHUB_JWT_SECRET=$(JWT_SECRET) \
			-e ORIGINHUB_GIT_REPO__ROOT=$(GIT_REPO_ROOT) \
			-e SPRING_DATA_REDIS_HOST=$(REDIS_NAME) \
			-e SPRING_DATA_REDIS_PORT=$(REDIS_PORT) \
			-e SPRING_PROFILES_ACTIVE=$(SPRING_PROFILE) \
			-e ORIGINHUB_ADMIN_MODULITH_EVENTS_ENABLED=true \
			-e ORIGINHUB_ADMIN_ENABLED=$(ADMIN_ENABLED) \
			$(if $(ACTIONS_ENCRYPTION_KEY),-e ACTIONS_ENCRYPTION_KEY=$(ACTIONS_ENCRYPTION_KEY),) \
			-e OAUTH2_GOOGLE_CLIENT_ID=$(GOOGLE_CLIENT_ID) \
			-e OAUTH2_GOOGLE_CLIENT_SECRET=$(GOOGLE_CLIENT_SECRET) \
			-e OAUTH2_GITHUB_CLIENT_ID=$(GITHUB_CLIENT_ID) \
			-e OAUTH2_GITHUB_CLIENT_SECRET=$(GITHUB_CLIENT_SECRET) \
			-e OAUTH2_GITLAB_CLIENT_ID=$(GITLAB_CLIENT_ID) \
			-e OAUTH2_GITLAB_CLIENT_SECRET=$(GITLAB_CLIENT_SECRET) \
			-v $(REPOS_VOLUME):$(GIT_REPO_ROOT) \
			$(IMAGE)

app-stop:
	-docker stop $(APP_NAME)
	-docker rm   $(APP_NAME)

# ── Local development ─────────────────────────────────────────────────────────

dev-setup:
	@test -f $(LOCAL_CONFIG) || (cp $(LOCAL_CONFIG_EXAMPLE) $(LOCAL_CONFIG) && echo "Created $(LOCAL_CONFIG) from example.")
	@$(MAKE) infra
	@cd originhub-frontend && pnpm install
	@cd originhub-admin-panel && pnpm install
	@cd e2e && pnpm install
	@echo ""
	@echo "  Dev setup complete"
	@echo "  1. make dev-backend          → API at http://localhost:8080"
	@echo "  2. cd originhub-frontend && pnpm start  → UI at http://localhost:4200"
	@echo "  Bootstrap admin: admin / Admin123"
	@echo ""

dev-backend:
	./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local

# ── Tests (no running server required) ────────────────────────────────────────

test: test-backend test-runner test-lint
	@echo "All tests passed."

test-backend:
	./mvnw test

test-runner:
	$(MAKE) -C $(RUNNER_DIR) test

test-lint:
	cd originhub-frontend && pnpm lint
	cd originhub-admin-panel && pnpm lint
	cd e2e && pnpm lint

verify:
	./mvnw verify

# ── Kubernetes + Argo CD (local kind) ─────────────────────────────────────────

K8S_NAMESPACE         ?= originhub
K8S_RELEASE           ?= originhub
KIND_CLUSTER_NAME     ?= originhub
DELETE_KIND           ?= 1
K8S_CHART             := deploy/helm/originhub
# Component flags (1=on, 0=off) — passed to Argo CD Helm parameters
K8S_ADMIN_API         ?= 1
K8S_FRONTEND          ?= 1
K8S_ADMIN_PANEL       ?= 1
K8S_OBSERVABILITY     ?= 1
K8S_PROMETHEUS_INGRESS ?= 0
K8S_DOMAIN            ?= originhub.test
K8S_API_HOST          ?= api.$(K8S_DOMAIN)
K8S_FRONTEND_HOST     ?= app.$(K8S_DOMAIN)
K8S_ADMIN_PANEL_HOST  ?= admin.$(K8S_DOMAIN)
K8S_GRAFANA_HOST      ?= grafana.$(K8S_DOMAIN)
K8S_ARGOCD_HOST       ?= argocd.$(K8S_DOMAIN)
K8S_PROMETHEUS_HOST   ?= prometheus.$(K8S_DOMAIN)
ORIGINHUB_LOCAL_BUILD ?= 1
KIND_HTTP_PORT        ?= 80
KIND_HTTPS_PORT       ?= 443

k8s-bootstrap:
	chmod +x deploy/scripts/bootstrap.sh
	ORIGINHUB_LOCAL_BUILD=$(ORIGINHUB_LOCAL_BUILD) ORIGINHUB_GIT_REPO_URL=$(ORIGINHUB_GIT_REPO_URL) \
	  K8S_ADMIN_API=$(K8S_ADMIN_API) K8S_FRONTEND=$(K8S_FRONTEND) K8S_ADMIN_PANEL=$(K8S_ADMIN_PANEL) \
	  K8S_OBSERVABILITY=$(K8S_OBSERVABILITY) K8S_PROMETHEUS_INGRESS=$(K8S_PROMETHEUS_INGRESS) \
	  K8S_FRONTEND_HOST=$(K8S_FRONTEND_HOST) K8S_ADMIN_PANEL_HOST=$(K8S_ADMIN_PANEL_HOST) \
	  K8S_GRAFANA_HOST=$(K8S_GRAFANA_HOST) K8S_ARGOCD_HOST=$(K8S_ARGOCD_HOST) \
	  K8S_PROMETHEUS_HOST=$(K8S_PROMETHEUS_HOST) K8S_API_HOST=$(K8S_API_HOST) K8S_DOMAIN=$(K8S_DOMAIN) \
	  KIND_HTTP_PORT=$(KIND_HTTP_PORT) KIND_HTTPS_PORT=$(KIND_HTTPS_PORT) \
	  ./deploy/scripts/bootstrap.sh

# Recreate kind with default ports 80/443 — no purge, no image rebuild (~5 min)
k8s-fix-ports:
	$(MAKE) k8s-bootstrap ORIGINHUB_LOCAL_BUILD=0

k8s-hosts:
	chmod +x deploy/scripts/ensure-hosts.sh
	K8S_DOMAIN=$(K8S_DOMAIN) K8S_FRONTEND=$(K8S_FRONTEND) K8S_ADMIN_PANEL=$(K8S_ADMIN_PANEL) \
	  K8S_OBSERVABILITY=$(K8S_OBSERVABILITY) K8S_PROMETHEUS_INGRESS=$(K8S_PROMETHEUS_INGRESS) \
	  K8S_API_HOST=$(K8S_API_HOST) K8S_FRONTEND_HOST=$(K8S_FRONTEND_HOST) \
	  K8S_ADMIN_PANEL_HOST=$(K8S_ADMIN_PANEL_HOST) K8S_GRAFANA_HOST=$(K8S_GRAFANA_HOST) \
	  K8S_ARGOCD_HOST=$(K8S_ARGOCD_HOST) K8S_PROMETHEUS_HOST=$(K8S_PROMETHEUS_HOST) \
	  ./deploy/scripts/ensure-hosts.sh

k8s-template:
	helm template $(K8S_RELEASE) $(K8S_CHART) \
	  -f $(K8S_CHART)/values.yml \
	  -n $(K8S_NAMESPACE)

k8s-purge:
	chmod +x deploy/scripts/k8s-purge.sh
	KIND_CLUSTER_NAME=$(KIND_CLUSTER_NAME) K8S_NAMESPACE=$(K8S_NAMESPACE) K8S_RELEASE=$(K8S_RELEASE) DELETE_KIND=$(DELETE_KIND) ./deploy/scripts/k8s-purge.sh

k8s-kubeconfig:
	chmod +x deploy/scripts/k8s-kubeconfig.sh
	KIND_CLUSTER_NAME=$(KIND_CLUSTER_NAME) ./deploy/scripts/k8s-kubeconfig.sh

k8s-uninstall: k8s-purge

sync-grafana-chart:
	cp monitoring/grafana/provisioning/dashboards/originhub.json deploy/helm/originhub/config/grafana/dashboards/
	cp monitoring/grafana/provisioning/dashboards/dashboard.yml deploy/helm/originhub/config/grafana/dashboards/

# ── Logs ──────────────────────────────────────────────────────────────────────

logs:
	docker logs -f $(APP_NAME)

logs-db:
	docker logs -f $(POSTGRES_NAME)

logs-redis:
	docker logs -f $(REDIS_NAME)

logs-prometheus:
	docker logs -f $(PROMETHEUS_NAME)

logs-grafana:
	docker logs -f $(GRAFANA_NAME)

# ── Runner (Go agent) ─────────────────────────────────────────────────────────

runner-build:
	$(MAKE) -C $(RUNNER_DIR) build
	@echo "Runner binary → $(RUNNER_DIR)/dist/$(RUNNER_NAME)"

runner-build-all:
	$(MAKE) -C $(RUNNER_DIR) build-all
	@echo "Runner binaries → $(RUNNER_DIR)/dist/"

# ── Misc ──────────────────────────────────────────────────────────────────────

build:
	docker compose build --no-cache

ldap-up:
	-docker rm -f $(LDAP_NAME)
	docker run -d --name $(LDAP_NAME) -p $(LDAP_PORT):10389 $(LDAP_IMAGE)
	@echo "LDAP test server → ldap://localhost:$(LDAP_PORT) (container listens on 10389)"
	@echo "E2E: cd e2e && E2E_LDAP_ENABLED=1 pnpm test:e2e:ldap"

ldap-down:
	-docker rm -f $(LDAP_NAME)

saml-keygen:
	@mkdir -p ~/.originhub/saml
	@openssl req -newkey rsa:2048 -nodes \
	  -keyout ~/.originhub/saml/sp-signing.key \
	  -x509 -days 3650 \
	  -out ~/.originhub/saml/sp-signing.crt \
	  -subj "/CN=originhub-sp/O=OriginHub/C=TR"
	@echo "SAML SP key pair generated at ~/.originhub/saml/"
	@echo "  Key : ~/.originhub/saml/sp-signing.key"
	@echo "  Cert: ~/.originhub/saml/sp-signing.crt"

# 32-byte AES-256 key for Actions workflow secrets vault (base64)
actions-encryption-key:
	@mkdir -p ~/.originhub
	@KEY=$$(openssl rand -base64 32 | tr -d '\n'); \
	echo "$$KEY" > ~/.originhub/actions-encryption-key; \
	chmod 600 ~/.originhub/actions-encryption-key; \
	echo "Actions encryption key → ~/.originhub/actions-encryption-key"; \
	echo ""; \
	echo "  export ACTIONS_ENCRYPTION_KEY=$$KEY"; \
	echo ""; \
	echo "  Helm deploy/helm/originhub/local.yml (k8s secret data):"; \
	echo "    ACTIONS_ENCRYPTION_KEY: $$(echo -n "$$KEY" | base64)"; \
	echo ""; \
	echo "  application-local.yaml:"; \
	echo "    originhub.actions.secrets.encryption-key: $$KEY"

sync-postgres-logs:
	@mkdir -p $(HOME)/.originhub/postgres-logs
	docker cp $(POSTGRES_NAME):/var/log/postgresql/. $(HOME)/.originhub/postgres-logs/
	@echo "Postgres logs copied to $(HOME)/.originhub/postgres-logs"

ps:
	docker ps --filter "network=$(NETWORK)" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

purge: down
	-docker volume rm $(REPOS_VOLUME)
	-docker volume rm $(POSTGRES_LOGS_VOLUME)
	@echo "All OriginHub resources removed (containers + volumes)."

help:
	@echo ""
	@echo "  OriginHub Makefile"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make up                → Postgres + Redis + app container"
	@echo "  make down              → Stop & remove all containers"
	@echo "  make start             → Start stopped containers"
	@echo "  make stop              → Stop containers (keep them)"
	@echo "  make restart           → stop + start"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make infra             → docker compose up (pg + redis only)"
	@echo "  make monitoring        → Start Prometheus + Grafana (optional)"
	@echo "  make monitoring-down   → Stop Prometheus + Grafana"
	@echo "  make infra-down        → docker compose down"
	@echo "  make infra-stop        → docker compose stop"
	@echo "  make infra-start       → docker compose start"
	@echo "  make app               → Start app container only"
	@echo "  make app-stop          → Stop & remove app container"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make logs              → Follow app logs"
	@echo "  make logs-db           → Follow Postgres logs"
	@echo "  make logs-redis        → Follow Redis logs"
	@echo "  make logs-prometheus   → Follow Prometheus logs"
	@echo "  make logs-grafana      → Follow Grafana logs"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make build             → Rebuild images (no cache)"
	@echo "  make ps                → Show running containers"
	@echo "  make purge             → down + delete volumes ⚠"
	@echo "  make runner-build      → Build runner binary for local arch ($(RUNNER_DIR)/dist/)"
	@echo "  make runner-build-all  → Build runner for Linux amd64/arm64, macOS arm64, Windows amd64"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make ldap-up           → Start Docker OpenLDAP for LDAP E2E (port $(LDAP_PORT):10389)"
	@echo "  make ldap-down         → Stop & remove LDAP test container"
	@echo "  make saml-keygen       → Generate SP signing key pair (~/.originhub/saml/)"
	@echo "  make actions-encryption-key → Actions secrets vault key (~/.originhub/actions-encryption-key)"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make dev-setup         → Infra + pnpm install + local config template"
	@echo "  make dev-backend       → Run backend with local profile (:8080)"
	@echo "  ADMIN_ENABLED=false make up → Docker stack without admin API"
	@echo "  make test              → Backend + runner + frontend lint"
	@echo "  make test-backend      → ./mvnw test"
	@echo "  make test-runner       → Go tests"
	@echo "  make test-lint         → ESLint (frontend, admin, e2e)"
	@echo "  make verify            → Full backend CI gate"
	@echo "  ──────────────────────────────────────────────────────"
	@echo "  make k8s-bootstrap     → K8s + Argo CD + OriginHub (LOCAL=1 for kind — see deploy/README.md)"
	@echo "                         Flags: K8S_ADMIN_PANEL=0 K8S_FRONTEND=1 K8S_PROMETHEUS_INGRESS=1"
	@echo "                         K8S_ADMIN_API=0 disables backend admin API"
	@echo "  make k8s-install       → Helm only on current cluster (needs prod.yml)"
	@echo "  make k8s-template      → Render Helm manifests"
	@echo "  make k8s-kubeconfig    → Refresh ~/.kube/config for kind cluster"
	@echo "  make k8s-purge         → Remove all K8s stack (Helm, namespaces, kind cluster) ⚠"
	@echo "  make k8s-uninstall     → Alias for k8s-purge"
	@echo "  make help              → This message"
	@echo ""
