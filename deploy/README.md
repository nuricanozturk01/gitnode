# Kubernetes + Argo CD

Local **kind** cluster with GitOps: backend, frontend, admin panel, Postgres, Redis, optional Prometheus/Grafana.

Each UI component has its own Docker image under `deploy/docker/`:

| Image | Dockerfile |
|-------|------------|
| `originhub-backend:local` | `deploy/docker/backend/Dockerfile` |
| `originhub-frontend:local` | `deploy/docker/frontend/Dockerfile` |
| `originhub-admin-panel:local` | `deploy/docker/admin-panel/Dockerfile` |

## Prerequisites

Install before running `make k8s-bootstrap`.

| Tool | Purpose |
|------|---------|
| [Docker](https://docs.docker.com/get-docker/) | kind node + image builds (Engine must be **running**) |
| [kind](https://kind.sigs.k8s.io/) | Local Kubernetes cluster |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | Cluster CLI |
| [Helm 3](https://helm.sh/docs/intro/install/) | Chart install |

### Install kind (macOS)

```bash
brew install kind kubectl helm
```

### Install kind (Linux)

```bash
curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind
```

### Verify

```bash
docker info
kind version
kubectl version --client
helm version
```

---

## One-shot bootstrap

```bash
make k8s-purge          # optional — clean slate
make k8s-bootstrap      # kind + cert-manager + ingress + Argo CD + OriginHub
```

First run ~10–20 minutes (builds backend + frontend + admin-panel images). `/etc/hosts` is updated automatically (sudo prompt).

| Service | URL | Default |
|---------|-----|---------|
| Frontend (SPA) | http://app.originhub.test | ✅ |
| API | http://api.originhub.test | ✅ |
| Admin panel | http://admin.originhub.test | ✅ (`admin` / `Admin123`) |
| Grafana | http://grafana.originhub.test | ✅ (`admin` / `admin`) |
| Argo CD | http://argocd.originhub.test | ✅ |
| Git SSH | `git@127.0.0.1:30222` | ✅ |

Argo CD admin password:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d && echo
```

### Component flags (Makefile → Argo CD)

| Flag | Default | Effect |
|------|---------|--------|
| `K8S_FRONTEND=1` | on | Build + deploy frontend at `app.originhub.test` |
| `K8S_ADMIN_PANEL=1` | on | Build + deploy admin panel at `admin.originhub.test` |
| `K8S_OBSERVABILITY=1` | on | Deploy Grafana + Prometheus |
| `K8S_ADMIN_API=1` | on | Backend admin API |
| `K8S_PROMETHEUS_INGRESS=1` | off | Prometheus UI at `prometheus.originhub.test` |
| `ORIGINHUB_LOCAL_BUILD=0` | on | Skip local image builds (uses registry tags) |

Examples:

```bash
# No admin panel
K8S_ADMIN_PANEL=0 make k8s-bootstrap

# No Grafana / Prometheus
K8S_OBSERVABILITY=0 make k8s-bootstrap

# Prometheus UI via ingress
K8S_PROMETHEUS_INGRESS=1 make k8s-bootstrap
```

### Troubleshooting

**Ports 80/443 in use** — bootstrap fails with a clear error. Free the port or use alternate ports:

```bash
KIND_HTTP_PORT=9080 KIND_HTTPS_PORT=9443 make k8s-bootstrap
```

Then append `:9080` to all URLs (e.g. `http://argocd.originhub.test:9080`).

**DNS slow / site won't open on macOS?** Use `*.originhub.test` (default) — not `*.local` (macOS mDNS conflict). Refresh hosts only:

```bash
make k8s-hosts
```

**Wrong ingress port (9080 instead of 80)?** No purge needed — kind is recreated automatically:

```bash
make k8s-fix-ports    # ~5 min, reuses existing Docker images
```

**Stale kubeconfig:**

```bash
make k8s-kubeconfig
kubectl get pods -n originhub
```

**Full reset:**

```bash
make k8s-purge && make k8s-bootstrap
```

---

## Full teardown

```bash
make k8s-purge
```

Removes Helm releases, namespaces (including PVCs), cert-manager CRDs, and the kind cluster.

---

## Configuration

Single values file: **`deploy/helm/originhub/values.yml`**

```yaml
domain:
  apiHost: api.originhub.test
  frontendUrl: http://app.originhub.test   # CORS + OAuth redirects
  grafanaHost: grafana.originhub.test

frontend:
  enabled: true
  host: app.originhub.test

adminPanel:
  enabled: true
  host: admin.originhub.test

monitoring:
  enabled: true
```

Helm sets automatically:

- `ORIGINHUB_FRONTEND_BASE_URL` ← `domain.frontendUrl`
- `ORIGINHUB_CORS_ALLOWED_ORIGINS` ← `frontendUrl` + `extraCorsOrigins`
- Backend ingress ← `domain.apiHost`
- Grafana ingress ← `domain.grafanaHost`

### Secrets (base64)

```bash
echo -n 'my-jwt-secret-min-32-chars........' | base64
make actions-encryption-key   # Actions secrets vault key
```

| Key | Location |
|-----|----------|
| `common.secrets.ORIGINHUB_JWT_SECRET` | `values.yml` |
| `postgres.secrets.*` | `values.yml` |
| `originhub.secrets.ACTIONS_ENCRYPTION_KEY` | `values.yml` |

---

## Helm commands

```bash
make k8s-template          # render manifests (debug)
make k8s-kubeconfig        # refresh ~/.kube/config
make k8s-purge             # full teardown
```

Manual Helm:

```bash
helm template originhub deploy/helm/originhub \
  -f deploy/helm/originhub/values.yml \
  -n originhub
```

---

## Layout

```
deploy/
├── docker/
│   ├── backend/Dockerfile
│   ├── frontend/Dockerfile
│   └── admin-panel/Dockerfile
├── helm/originhub/
│   ├── values.yml
│   └── templates/
├── argocd/
│   ├── applications/originhub.yml
│   ├── project.yml
│   └── values.yaml
├── kind/kind-config.yaml
└── scripts/
    ├── bootstrap.sh
    └── k8s-purge.sh
```

## Argo CD

Bootstrap registers:

- **AppProject:** `originhub` (`deploy/argocd/project.yml`)
- **Application:** `originhub` (`deploy/argocd/applications/originhub.yml`)

Git repo URL is auto-detected from `git remote get-url origin`. Override: `ORIGINHUB_GIT_REPO_URL`.

OriginHub is deployed **by Argo CD** from `deploy/helm/originhub` — push `deploy/` changes to Git for sync.

## Components

| Resource | Purpose |
|----------|---------|
| originhub-backend | Spring Boot API + Git SSH |
| originhub-frontend | Angular SPA (nginx) |
| originhub-admin-panel | Platform admin UI (nginx) |
| originhub-postgres | PostgreSQL 17 |
| originhub-redis | Redis 7 |
| originhub-prometheus | Scrapes `/actuator/prometheus` |
| originhub-grafana | Pre-provisioned OriginHub dashboard |

Set `monitoring.enabled: false` or `K8S_OBSERVABILITY=0` to disable Prometheus/Grafana.

Set `adminPanel.enabled: false` or `K8S_ADMIN_PANEL=0` to skip admin panel.

Set `frontend.enabled: false` or `K8S_FRONTEND=0` to skip frontend.

## Multi-instance

`replicaCount > 1` requires `persistence.repos.accessMode: ReadWriteMany` (NFS, EFS, Longhorn RWX, …).
