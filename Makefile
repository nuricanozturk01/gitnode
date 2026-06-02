# OriginHub - Docker Makefile
# Usage: make <target>

NETWORK       := originhub
POSTGRES_NAME := originhub-postgres
REDIS_NAME    := originhub-redis
APP_NAME      := originhub
IMAGE         := repo.repsy.io/nuricanozturk/originhub/originhub-os:latest

POSTGRES_DB    := originhub
POSTGRES_USER  := admin
POSTGRES_PASS  := admin123

JWT_SECRET     := 995a44f7111b23ebed8ad37e8b9cbe380dd5022f8b3bf67b16c8e223456f74a0
GIT_REPO_ROOT  := /data/repos
REPOS_VOLUME   := originhub-repos
SPRING_PROFILE := os

HTTP_PORT := 8080
SSH_PORT  := 2222

# Google Client
GOOGLE_CLIENT_ID     := YOUR_CLIENT
GOOGLE_CLIENT_SECRET := YOUR_SECRET

# Github Client
GITHUB_CLIENT_ID     := YOUR_CLIENT
GITHUB_CLIENT_SECRET := YOUR_SECRET

# Gitlab Client
GITLAB_CLIENT_ID     := YOUR_CLIENT
GITLAB_CLIENT_SECRET := YOUR_SECRET

# ──────────────────────────────────────────────
.PHONY: all up down start stop restart logs logs-db ps \
  network network-rm \
  db redis app \
  clean purge help

all: up

up: network db redis app
	@echo "OriginHub is up → http://localhost:$(HTTP_PORT)  |  SSH :$(SSH_PORT)"

down:
	@echo "Stopping containers..."
	-docker stop $(APP_NAME) $(REDIS_NAME) $(POSTGRES_NAME)
	-docker rm   $(APP_NAME) $(REDIS_NAME) $(POSTGRES_NAME)

start:
	docker start $(POSTGRES_NAME)
	docker start $(REDIS_NAME)
	@sleep 2
	docker start $(APP_NAME)

stop:
	docker stop $(APP_NAME) $(REDIS_NAME) $(POSTGRES_NAME)

restart: stop start

logs:
	docker logs -f $(APP_NAME)

logs-db:
	docker logs -f $(POSTGRES_NAME)

ps:
	docker ps --filter "network=$(NETWORK)" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

network:
	@docker network inspect $(NETWORK) > /dev/null 2>&1 \
		|| (echo "Creating network $(NETWORK)..." && docker network create $(NETWORK))

network-rm:
	-docker network rm $(NETWORK)

db: network
	@docker ps -a --format "{{.Names}}" | grep -q "^$(POSTGRES_NAME)$$" \
		&& echo "$(POSTGRES_NAME) already exists, skipping..." \
		|| docker run -d \
			--name $(POSTGRES_NAME) \
			--network $(NETWORK) \
			-e POSTGRES_DB=$(POSTGRES_DB) \
			-e POSTGRES_USER=$(POSTGRES_USER) \
			-e POSTGRES_PASSWORD=$(POSTGRES_PASS) \
			postgres:17
	@echo "Waiting for Postgres to be ready..."
	@until docker exec $(POSTGRES_NAME) pg_isready -U $(POSTGRES_USER) > /dev/null 2>&1; do sleep 1; done
	@echo "Postgres ready."

redis: network
	@docker ps -a --format "{{.Names}}" | grep -q "^$(REDIS_NAME)$$" \
		&& echo "$(REDIS_NAME) already exists, skipping..." \
		|| docker run -d \
			--name $(REDIS_NAME) \
			--network $(NETWORK) \
			redis:7-alpine redis-server --save ""
	@echo "Redis ready."

app: network
	@docker ps -a --format "{{.Names}}" | grep -q "^$(APP_NAME)$$" \
		&& echo "$(APP_NAME) already exists, skipping..." \
		|| docker run -d \
			--name $(APP_NAME) \
			--network $(NETWORK) \
			-p $(HTTP_PORT):8080 \
			-p $(SSH_PORT):2222 \
			-e SPRING_DATASOURCE_URL=jdbc:postgresql://$(POSTGRES_NAME):5432/$(POSTGRES_DB) \
			-e SPRING_DATASOURCE_USERNAME=$(POSTGRES_USER) \
			-e SPRING_DATASOURCE_PASSWORD=$(POSTGRES_PASS) \
			-e ORIGINHUB_JWT_SECRET=$(JWT_SECRET) \
			-e ORIGINHUB_GIT_REPO__ROOT=$(GIT_REPO_ROOT) \
			-e SPRING_DATA_REDIS_HOST=$(REDIS_NAME) \
			-e SPRING_DATA_REDIS_PORT=6379 \
			-e SPRING_PROFILES_ACTIVE=$(SPRING_PROFILE) \
			-e OAUTH2_GOOGLE_CLIENT_ID=$(GOOGLE_CLIENT_ID) \
			-e OAUTH2_GOOGLE_CLIENT_SECRET=$(GOOGLE_CLIENT_SECRET) \
			-e OAUTH2_GITHUB_CLIENT_ID=$(GITHUB_CLIENT_ID) \
			-e OAUTH2_GITHUB_CLIENT_SECRET=$(GITHUB_CLIENT_SECRET) \
			-e OAUTH2_GITLAB_CLIENT_ID=$(GITLAB_CLIENT_ID) \
			-e OAUTH2_GITLAB_CLIENT_SECRET=$(GITLAB_CLIENT_SECRET) \
			-v $(REPOS_VOLUME):$(GIT_REPO_ROOT) \
			$(IMAGE)

clean: down network-rm
	@echo "Cleaned up containers and network."

purge: down network-rm
	-docker volume rm $(REPOS_VOLUME)
	@echo "All OriginHub resources removed."

logs-redis:
	docker logs -f $(REDIS_NAME)

help:
	@echo ""
	@echo "  OriginHub Makefile targets"
	@echo "  ─────────────────────────────────────────────"
	@echo "  make up          → Create network, start db & app"
	@echo "  make down        → Stop & remove containers"
	@echo "  make start       → Start stopped containers"
	@echo "  make stop        → Stop containers (keep them)"
	@echo "  make restart     → stop + start"
	@echo "  make logs        → Follow app logs"
	@echo "  make logs-db     → Follow db logs"
	@echo "  make ps          → Show running containers"
	@echo "  make clean       → Remove containers + network"
	@echo "  make purge       → clean + delete volumes"
	@echo "  make help        → This message"
	@echo ""
