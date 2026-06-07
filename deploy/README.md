# Kubernetes + Argo CD

OriginHub **backend only** (API, Git HTTP/SSH, Postgres, Redis). Frontend is deployed separately on **Vercel** or **Cloudflare Pages** — configure its API URL to `domain.apiHost`.

Pattern follows [setupshowroom-helm](https://github.com/nuricanozturk/setupshowroom-helm): `values.yml` + env overlay (`local.yml` / `prod.yml`), secrets as base64.

## Prerequisites

Install these **before** running `make k8s-bootstrap`.

| Tool | Required when | Purpose |
|------|---------------|---------|
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | Always | Cluster CLI |
| [Helm 3](https://helm.sh/docs/intro/install/) | Always | Chart install |
| [Docker](https://docs.docker.com/get-docker/) | `LOCAL=1` | Runs the kind cluster (Engine must be **running**) |
| [kind](https://kind.sigs.k8s.io/) | `LOCAL=1` only | Local Kubernetes cluster in Docker |

**Server / existing cluster:** `kubectl` + `Helm` only. Point `kubectl` at your cluster (`kubectl config current-context`).

### Install kind

<details>
<summary><strong>macOS</strong></summary>

```bash
brew install kind
# or: curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-darwin-arm64   # Apple Silicon
# or: curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-darwin-amd64   # Intel
# chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind
```

</details>

<details>
<summary><strong>Linux</strong></summary>

```bash
# apt (Debian/Ubuntu) — kind is not in default repos; use binary:
curl -Lo ./kind https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# or Go:
go install sigs.k8s.io/kind@latest
```

</details>

<details>
<summary><strong>Windows</strong></summary>

Use [WSL2](https://learn.microsoft.com/en-us/windows/wsl/install) + Docker Desktop, then follow the **Linux** steps inside WSL.

Or [Chocolatey](https://community.chocolatey.org/packages/kind):

```powershell
choco install kind
```

</details>

### Install kubectl & Helm

<details>
<summary><strong>macOS</strong></summary>

```bash
brew install kubectl helm
```

</details>

<details>
<summary><strong>Linux</strong></summary>

```bash
# kubectl — see https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl && sudo mv kubectl /usr/local/bin/

# Helm — see https://helm.sh/docs/intro/install/
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

</details>

<details>
<summary><strong>Windows</strong></summary>

```powershell
choco install kubernetes-cli kubernetes-helm
```

Or use WSL2 and the Linux instructions.

</details>

### Verify

```bash
docker info          # LOCAL=1 — must not error
kind version         # LOCAL=1
kubectl version --client
helm version
```

### Without kind

If you already have a cluster (cloud, k3s, minikube, Docker Desktop Kubernetes, etc.):

```bash
kubectl config use-context <your-context>
make k8s-bootstrap              # no LOCAL=1
```

---

## One-shot bootstrap

### Local (kind)

Requires [Prerequisites](#prerequisites) (`kind`, Docker, `kubectl`, `helm`). First run ~10–15 minutes.

```bash
make k8s-bootstrap LOCAL=1
```

Installs: kind cluster · cert-manager · ingress-nginx · **Argo CD** · OriginHub Helm chart.

**Apple Silicon (M1/M2/M3):** kind nodes are `linux/arm64`. Registry image is multi-arch (native amd64 + arm64 builds in CI, no QEMU). Until a fresh deploy is pushed, bootstrap may still **build locally** on arm64 Macs (`originhub-os:local`).

Add to `/etc/hosts` (macOS/Linux: `/etc/hosts`, Windows: `C:\Windows\System32\drivers\etc\hosts`):

```
127.0.0.1  api.originhub.local  argocd.originhub.local  grafana.originhub.local
```

| Service | URL |
|---------|-----|
| Argo CD UI | http://argocd.originhub.local (admin / see below) |
| OriginHub API | http://api.originhub.local |
| Grafana | http://grafana.originhub.local (admin / admin) |
| Git SSH | `git@127.0.0.1:30222` (NodePort) |

Argo CD admin password:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d && echo
```

#### Troubleshooting: `context deadline exceeded` on ingress-nginx

Helm waits up to **10 minutes** (override with `HELM_TIMEOUT=20m`). First install is slow while Docker pulls the controller image.

#### Troubleshooting: ports 80/443 in use

Bootstrap **auto-switches to 9080/9443** and recreates the kind cluster if needed. URLs become `http://api.originhub.local:9080` (shown at end of bootstrap).

To force standard ports after freeing 80/443:

```bash
make k8s-purge
KIND_HTTP_PORT=80 KIND_HTTPS_PORT=443 make k8s-bootstrap LOCAL=1
```

#### Troubleshooting: `Kubernetes cluster unreachable` / `localhost:8080`

Usually a stale kubeconfig or unhealthy kind cluster after port conflicts. Reset:

```bash
make k8s-kubeconfig
# or: kind export kubeconfig --name originhub
kubectl get pods -n originhub
```

If still failing:

```bash
make k8s-purge
make k8s-bootstrap LOCAL=1
```

1. **Ports 80/443 busy** — handled automatically (9080/9443) or stop conflicting services.
2. **Retry after partial install:**
   ```bash
   make k8s-purge
   make k8s-bootstrap LOCAL=1
   ```
3. **Inspect pods** (while bootstrap runs or after failure):
   ```bash
   kubectl get pods -n ingress-nginx -w
   kubectl describe pod -n ingress-nginx -l app.kubernetes.io/component=controller
   ```
   `ImagePullBackOff` → check Docker network; `CrashLoopBackOff` on port bind → free 80/443 or use auto 9080/9443.

Local kind uses `deploy/kind/ingress-nginx-values.yaml` (hostPort 80/443 inside the node + no admission webhook).

### Server (existing cluster)

1. Copy and fill production overlay:

```bash
cp deploy/helm/originhub/prod.yml.example deploy/helm/originhub/prod.yml
# Edit domain.* and base64 secrets: echo -n 'secret' | base64
```

2. Bootstrap:

```bash
make k8s-bootstrap \
  ORIGINHUB_VALUE_FILE=prod.yml \
  ORIGINHUB_GIT_REPO_URL=https://github.com/you/originhub.git
```

`ORIGINHUB_GIT_REPO_URL` registers an Argo CD **Application** for GitOps sync.

## Full teardown

Remove everything `bootstrap.sh` installed — idempotent (safe to run when nothing is deployed):

```bash
make k8s-purge
```

This uninstalls Helm releases, deletes namespaces (including PVCs), removes cert-manager CRDs and the `selfsigned-issuer` ClusterIssuer, and deletes the local **kind** cluster (`originhub` by default).

On a **shared server cluster** where you must keep the Kubernetes cluster itself:

```bash
DELETE_KIND=0 make k8s-purge
```

Works on macOS, Linux, and Windows (Git Bash or WSL with `make`, `kubectl`, `helm`, and optionally `kind` on `PATH`).

## Domain configuration (single place)

Edit **`domain`** in `local.yml` or `prod.yml`:

```yaml
domain:
  apiHost: api.originhub.example.com      # Ingress → backend
  frontendUrl: https://originhub.example.com   # Vercel / Cloudflare Pages
  grafanaHost: grafana.originhub.example.com   # Grafana ingress
  extraCorsOrigins: []
```

Helm sets automatically:

- `ORIGINHUB_FRONTEND_BASE_URL` ← `domain.frontendUrl`
- `ORIGINHUB_CORS_ALLOWED_ORIGINS` ← `frontendUrl` + `extraCorsOrigins`
- Ingress host ← `domain.apiHost`
- Grafana ingress ← `domain.grafanaHost` (dashboards from `monitoring/grafana/provisioning/`)

### Monitoring (Prometheus + Grafana)

Enabled by default (`monitoring.enabled: true`).

| Service | Access |
|---------|--------|
| Grafana | `https://{domain.grafanaHost}` — login `admin` / see `monitoring.grafana.adminPassword` (local) or `monitoring.secrets.GF_SECURITY_ADMIN_PASSWORD` (prod, base64) |
| Prometheus | in-cluster only — `originhub-prometheus.originhub.svc:9090` |

When you update `monitoring/grafana/provisioning/dashboards/originhub.json`, copy to `deploy/helm/originhub/config/grafana/dashboards/originhub.json`.

### Frontend (Vercel / Cloudflare)

Build env on your frontend host:

```
VERCEL_API_BASE_URL=https://api.originhub.example.com
VERCEL_GIT_SSH_URL=git@api.originhub.example.com:2222
```

### OAuth callbacks (register at IdP)

| Provider | URL |
|----------|-----|
| Google | `https://{apiHost}/login/oauth2/code/google` |
| GitHub | `https://{apiHost}/login/oauth2/code/github` |
| GitLab | `https://{apiHost}/login/oauth2/code/gitlab` |

## Helm commands

```bash
# Render (debug)
make k8s-template ORIGINHUB_VALUE_FILE=local.yml

# Helm only (no Argo CD bootstrap)
make k8s-install

# Uninstall everything (Helm releases, namespaces, PVCs, cert-manager CRDs, kind cluster)
make k8s-purge

# Same as k8s-purge (alias)
make k8s-uninstall

# On a shared server cluster: keep the cluster, only remove bootstrap resources
DELETE_KIND=0 make k8s-purge
```

Manual Helm (setupshowroom style):

```bash
helm template originhub deploy/helm/originhub \
  -f deploy/helm/originhub/values.yml \
  -f deploy/helm/originhub/local.yml \
  -n originhub

helm upgrade --install originhub deploy/helm/originhub \
  -f deploy/helm/originhub/values.yml \
  -f deploy/helm/originhub/prod.yml \
  -n originhub --create-namespace
```

## Layout

```
deploy/
├── helm/originhub/           Backend chart
│   ├── values.yml            Base (empty secrets)
│   ├── local.yml             Local domain + dev secrets
│   ├── prod.yml.example      Production template
│   └── templates/
├── argocd/                   Argo CD config + Application manifests
├── kind/kind-config.yaml
└── scripts/
    ├── bootstrap.sh      One-shot installer
    └── k8s-purge.sh      Full teardown (reverse of bootstrap)
```

## Argo CD

After bootstrap, open **Argo CD UI** to watch sync status, diff, rollback, and health.

- AppProject: `originhub`
- Application: `originhub` (when `ORIGINHUB_GIT_REPO_URL` is set)
- Values: `deploy/argocd/values.yaml` (customize domain for Argo CD ingress)

## Secrets (base64)

```bash
echo -n 'my-jwt-secret-min-32-chars........' | base64
```

| Key | File |
|-----|------|
| `common.secrets.ORIGINHUB_JWT_SECRET` | values overlay |
| `postgres.secrets.*` | values overlay |
| `originhub.secrets.OAUTH2_*` | values overlay (optional) |

## Components

| Resource | Purpose |
|----------|---------|
| originhub-backend | Spring Boot API + Git SSH |
| originhub-postgres | PostgreSQL 17 |
| originhub-redis | Redis 7 |
| originhub-backend-ssh | LoadBalancer / NodePort :2222 |
| Ingress | HTTP(S) → backend :8080 |
| originhub-prometheus | Scrapes `/actuator/prometheus` |
| originhub-grafana | Pre-provisioned OriginHub dashboard |

Set `monitoring.enabled: false` in values overlay to disable Prometheus/Grafana.

Admin panel UI is **not** in this chart — optional, run separately.

## Multi-instance (Redis lock + shared volume)

The backend is designed for horizontal scale when **both** are true:

| Requirement | K8s chart |
|-------------|-----------|
| **Shared Redis** | All pods → `originhub-redis` Service (cache, rate limits, distributed locks, circuit-breaker keys) |
| **Shared Git volume** | PVC `originhub-repos` mounted at `/data/repos` (repos, SSH host keys under `.hosts/`, actions artifacts/cache on disk) |

**Redis distributed lock** — used by:

- Webhook DLQ retry scheduler (`lock:webhook:dlq:retry`) — one pod per window
- Bootstrap admin initializer — one pod creates platform admin on first start
- Circuit breaker state via `DistributedCircuitBreakerGuard` (Redis TTL keys)

With a single shared Redis Service, this works the same as Docker multi-instance.

**Shared volume caveat:**

| `replicaCount` | PVC `accessMode` |
|----------------|------------------|
| `1` (default) | `ReadWriteOnce` ✅ |
| `> 1` | **`ReadWriteMany` required** (NFS, EFS, Longhorn RWX, …) — set `persistence.repos.accessMode` + matching `storageClass` |

`ReadWriteOnce` + multiple replicas → second pod cannot mount the volume (or must land on the same node with unsupported multi-attach). JGit file locking only helps after the filesystem is actually shared.

**Helm env vs `application.yaml`:** chart sets DB, Redis, JWT, CORS, frontend URL, Git root, admin flag, observability, forward headers. Defaults apply for audit, actions, DLQ cron, SSO (off). Production should also set in `prod.yml`:

- `ACTIONS_ENCRYPTION_KEY` — if using Actions secrets vault
- `ORIGINHUB_PLATFORM_ADMIN_USERNAMES` — platform admin access
- OAuth secrets — if SSO providers enabled

