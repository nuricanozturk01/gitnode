#!/usr/bin/env bash
# Bootstrap Kubernetes + Argo CD + OriginHub backend (one shot).
#
# Local:  LOCAL=1 ./deploy/scripts/bootstrap.sh
# Server: ./deploy/scripts/bootstrap.sh  (kubectl context must point to your cluster)
#
# Optional:
#   ORIGINHUB_GIT_REPO_URL=https://github.com/you/originhub.git  → override auto-detected origin
#   ORIGINHUB_GIT_REVISION=main                                   → override branch/tag
#   ORIGINHUB_VALUE_FILE=local.yml|prod.yml
#   KIND_HTTP_PORT=9080 KIND_HTTPS_PORT=9443  → force ingress host ports (when 80/443 busy)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHART="${ROOT}/deploy/helm/originhub"
NAMESPACE="${K8S_NAMESPACE:-originhub}"
RELEASE="${K8S_RELEASE:-originhub}"
VALUE_FILE="${ORIGINHUB_VALUE_FILE:-local.yml}"
LOCAL="${LOCAL:-0}"
HELM_TIMEOUT="${HELM_TIMEOUT:-10m}"
ARGOCD_SYNC_TIMEOUT="${ARGOCD_SYNC_TIMEOUT:-15m}"
CLUSTER="${KIND_CLUSTER_NAME:-originhub}"

KIND_CONFIG=""
KUBECONFIG_FILE=""
HOST_HTTP_PORT=80
HOST_HTTPS_PORT=443
URL_SUFFIX=""

cleanup() {
  rm -f "$KIND_CONFIG" "$KUBECONFIG_FILE"
}
trap cleanup EXIT

need() {
  if command -v "$1" >/dev/null 2>&1; then
    return 0
  fi
  echo "Missing: $1"
  case "$1" in
    kind)
      echo ""
      echo "  LOCAL=1 needs kind (Kubernetes in Docker)."
      echo "  macOS:  brew install kind"
      echo "  Linux:  go install sigs.k8s.io/kind@latest  (or see https://kind.sigs.k8s.io/docs/user/quick-start/)"
      echo ""
      echo "  Or skip kind — use an existing cluster:"
      echo "    make k8s-bootstrap   # without LOCAL=1, kubectl context must be set"
      ;;
  esac
  exit 1
}

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  else
    return 1
  fi
}

resolve_ingress_host_ports() {
  if [[ -n "${KIND_HTTP_PORT:-}" ]]; then
    HOST_HTTP_PORT="${KIND_HTTP_PORT}"
    HOST_HTTPS_PORT="${KIND_HTTPS_PORT:-9443}"
    return 0
  fi
  if port_in_use 80 || port_in_use 443; then
    HOST_HTTP_PORT=9080
    HOST_HTTPS_PORT=9443
    echo "  Ports 80/443 busy → using host ports ${HOST_HTTP_PORT}/${HOST_HTTPS_PORT} for ingress."
    echo "  (Override: KIND_HTTP_PORT=80 KIND_HTTPS_PORT=443 after freeing those ports.)"
    echo ""
  fi
  if [[ "$HOST_HTTP_PORT" != "80" ]]; then
    URL_SUFFIX=":${HOST_HTTP_PORT}"
  fi
}

use_kind_kubeconfig() {
  KUBECONFIG_FILE="$(mktemp -t originhub-kubeconfig.XXXXXX)"
  kind get kubeconfig --name "$CLUSTER" >"$KUBECONFIG_FILE"
  export KUBECONFIG="$KUBECONFIG_FILE"
  kubectl config use-context "kind-${CLUSTER}" >/dev/null
}

verify_cluster() {
  if kubectl cluster-info --request-timeout=15s >/dev/null 2>&1; then
    return 0
  fi
  echo "  ✗ Kubernetes API unreachable (context: kind-${CLUSTER})."
  kubectl config view --minify 2>/dev/null || true
  return 1
}

ensure_kind_cluster() {
  resolve_ingress_host_ports
  KIND_CONFIG="$(mktemp -t originhub-kind.XXXXXX.yaml)"
  chmod +x "${ROOT}/deploy/kind/generate-kind-config.sh"
  "${ROOT}/deploy/kind/generate-kind-config.sh" "$HOST_HTTP_PORT" "$HOST_HTTPS_PORT" "$KIND_CONFIG"

  local recreate=0
  if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    use_kind_kubeconfig
    if ! verify_cluster; then
      echo "→ Kind cluster unhealthy; recreating..."
      recreate=1
    elif [[ "$HOST_HTTP_PORT" != "80" ]] && port_in_use 80; then
      echo "→ Recreating kind cluster with ingress ports ${HOST_HTTP_PORT}/${HOST_HTTPS_PORT}..."
      recreate=1
    fi
    if [[ "$recreate" == "1" ]]; then
      kind delete cluster --name "$CLUSTER"
    fi
  fi

  if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    echo "→ Creating kind cluster '${CLUSTER}' (ingress ${HOST_HTTP_PORT}/${HOST_HTTPS_PORT})..."
    kind create cluster --name "$CLUSTER" --config "$KIND_CONFIG"
  fi

  kind export kubeconfig --name "$CLUSTER"
  use_kind_kubeconfig
  if ! verify_cluster; then
    echo ""
    echo "  Fix: make k8s-purge && make k8s-bootstrap LOCAL=1"
    exit 1
  fi
}

ensure_kube_ready() {
  if [[ "$LOCAL" == "1" ]]; then
    use_kind_kubeconfig
    if ! verify_cluster; then
      echo ""
      echo "  Kubernetes API lost mid-bootstrap. Try:"
      echo "    make k8s-purge && make k8s-bootstrap LOCAL=1"
      exit 1
    fi
  fi
}

helm_install() {
  ensure_kube_ready
  local release="$1"
  local chart="$2"
  local ns="$3"
  shift 3

  echo "  namespace: ${ns}  timeout: ${HELM_TIMEOUT}"
  if [[ "$release" == "originhub" ]]; then
    local image_repo image_tag
    image_repo="$(awk '/repository:/{print $2; exit}' "${CHART}/${VALUE_FILE}" 2>/dev/null || true)"
    image_tag="$(awk '/^imageTag:/{print $2; exit}' "${CHART}/${VALUE_FILE}" 2>/dev/null || true)"
    [[ -z "$image_repo" ]] && image_repo="repo.repsy.io/nuricanozturk/originhub/originhub-os"
    [[ -z "$image_tag" ]] && image_tag="latest"
    echo "  image:     ${image_repo}:${image_tag}"
    echo "  (pull + postgres + migrations — first run often 5–15 min; status every 20s)"
    echo ""
  fi

  helm upgrade --install "$release" "$chart" -n "$ns" --create-namespace --wait --timeout "$HELM_TIMEOUT" "$@" &
  local helm_pid=$!
  local tick=0

  while kill -0 "$helm_pid" 2>/dev/null; do
    sleep 20
    tick=$((tick + 20))
    echo "  ── ${release} · ${tick}s ──"
    if kubectl get namespace "$ns" >/dev/null 2>&1; then
      kubectl get pods -n "$ns" -o wide 2>/dev/null || true
      local waiting
      waiting="$(kubectl get pods -n "$ns" -o jsonpath='{range .items[*]}{.metadata.name}{": "}{.status.containerStatuses[0].state.waiting.reason}{"\n"}{end}' 2>/dev/null | grep -v ': $' || true)"
      if [[ -n "$waiting" ]]; then
        echo "  waiting:"
        echo "$waiting" | sed 's/^/    /'
      fi
    else
      echo "    (namespace ${ns} not created yet)"
    fi
    echo ""
  done

  if ! wait "$helm_pid"; then
    echo ""
    echo "  ✗ Helm failed: ${release} (namespace: ${ns}, timeout: ${HELM_TIMEOUT})"
    echo ""
    kubectl get pods -n "$ns" -o wide 2>/dev/null || true
    echo ""
    kubectl get events -n "$ns" --sort-by='.lastTimestamp' 2>/dev/null | tail -20 || true
    echo ""
    case "$release" in
      ingress-nginx)
        echo "  Common fixes (local kind):"
        echo "    • make k8s-purge && make k8s-bootstrap LOCAL=1"
        echo "    • Longer wait: HELM_TIMEOUT=20m make k8s-bootstrap LOCAL=1"
        ;;
    esac
    exit 1
  fi
}

need kubectl
need helm

normalize_git_url() {
  local url="$1"
  if [[ "$url" =~ ^git@([^:]+):(.+)$ ]]; then
    echo "https://${BASH_REMATCH[1]}/${BASH_REMATCH[2]}"
  elif [[ "$url" =~ ^ssh://git@([^/]+)/(.+)$ ]]; then
    echo "https://${BASH_REMATCH[1]}/${BASH_REMATCH[2]}"
  else
    echo "$url"
  fi
}

resolve_git_repo() {
  if [[ -n "${ORIGINHUB_GIT_REPO_URL:-}" ]]; then
    normalize_git_url "${ORIGINHUB_GIT_REPO_URL}"
    return 0
  fi
  if git -C "$ROOT" remote get-url origin &>/dev/null; then
    normalize_git_url "$(git -C "$ROOT" remote get-url origin)"
    return 0
  fi
  return 1
}

resolve_git_revision() {
  if [[ -n "${ORIGINHUB_GIT_REVISION:-}" ]]; then
    echo "${ORIGINHUB_GIT_REVISION}"
    return 0
  fi
  git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main"
}

apply_originhub_application() {
  local app_file="${ROOT}/deploy/argocd/applications/originhub-prod.yml"
  [[ "$VALUE_FILE" == "local.yml" ]] && app_file="${ROOT}/deploy/argocd/applications/originhub-local.yml"

  local repo rev
  repo="$(resolve_git_repo)" || {
    echo ""
    echo "  ✗ Cannot resolve Git repo URL for Argo CD Application."
    echo "    Set ORIGINHUB_GIT_REPO_URL or run from a git clone with 'origin' remote."
    exit 1
  }
  rev="$(resolve_git_revision)"

  echo "→ Argo CD Application (GitOps)..."
  echo "  repo:       ${repo}"
  echo "  revision:   ${rev}"
  echo "  chart:      deploy/helm/originhub"
  echo "  values:     values.yml + ${VALUE_FILE}"
  echo ""
  sed -e "s|ORIGINHUB_GIT_REPO_URL|${repo}|g" \
    -e "s|ORIGINHUB_GIT_REVISION|${rev}|g" \
    "$app_file" | kubectl apply -f -
}

wait_for_originhub_application() {
  echo "→ Waiting for Argo CD to sync originhub (timeout: ${ARGOCD_SYNC_TIMEOUT})..."
  local start=$SECONDS
  local deadline=$((start + $(parse_duration_minutes "${ARGOCD_SYNC_TIMEOUT}")))
  while (( SECONDS < deadline )); do
    local sync health
    sync="$(kubectl get application originhub -n argocd -o jsonpath='{.status.sync.status}' 2>/dev/null || true)"
    health="$(kubectl get application originhub -n argocd -o jsonpath='{.status.health.status}' 2>/dev/null || true)"
    echo "  ── originhub · $((SECONDS - start))s ── sync=${sync:-Pending} health=${health:-Pending}"
    kubectl get pods -n "$NAMESPACE" -o wide 2>/dev/null || true
    echo ""
    if [[ "$sync" == "Synced" && "$health" == "Healthy" ]]; then
      echo "  ✓ Argo CD Application originhub is Synced and Healthy."
      return 0
    fi
    sleep 20
  done
  echo ""
  echo "  ⚠ Argo CD sync timed out. Check UI → Applications → originhub"
  kubectl get application originhub -n argocd -o yaml 2>/dev/null | tail -40 || true
  return 1
}

parse_duration_minutes() {
  local t="$1"
  if [[ "$t" =~ ^([0-9]+)m$ ]]; then
    echo $((BASH_REMATCH[1] * 60))
  elif [[ "$t" =~ ^([0-9]+)s$ ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo 900
  fi
}

if [[ "$LOCAL" == "1" ]]; then
  need kind
  ensure_kind_cluster
fi

echo "→ Helm repos..."
helm repo add argo https://argoproj.github.io/argo-helm >/dev/null 2>&1 || true
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
helm repo add jetstack https://charts.jetstack.io >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "→ cert-manager..."
helm_install cert-manager jetstack/cert-manager cert-manager \
  --set crds.enabled=true
kubectl apply -f "${ROOT}/deploy/argocd/clusterissuer-local.yml"

echo "→ ingress-nginx (may take several minutes on first pull)..."
INGRESS_EXTRA=()
if [[ "$LOCAL" == "1" ]]; then
  INGRESS_EXTRA=(-f "${ROOT}/deploy/kind/ingress-nginx-values.yaml")
else
  INGRESS_EXTRA=(--set controller.watchIngressWithoutClass=true)
fi
helm_install ingress-nginx ingress-nginx/ingress-nginx ingress-nginx \
  "${INGRESS_EXTRA[@]}"

echo "→ Argo CD..."
helm_install argocd argo/argo-cd argocd \
  -f "${ROOT}/deploy/argocd/values.yaml"

echo "→ Argo CD AppProject..."
kubectl apply -f "${ROOT}/deploy/argocd/project.yml"

apply_originhub_application
wait_for_originhub_application || true

ARGO_PASS="$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' 2>/dev/null | base64 -d || true)"
GIT_REPO_DISPLAY="$(resolve_git_repo 2>/dev/null || echo unknown)"
GIT_REV_DISPLAY="$(resolve_git_revision)"

if [[ "$LOCAL" == "1" ]]; then
  kind export kubeconfig --name "$CLUSTER"
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Bootstrap complete"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "  Add to /etc/hosts (required — URLs won't open in browser without this):"
echo "    127.0.0.1  api.originhub.local  argocd.originhub.local  grafana.originhub.local"
echo ""
echo "  Argo CD UI:     http://argocd.originhub.local${URL_SUFFIX}"
echo "  Application: originhub  (${GIT_REPO_DISPLAY} @ ${GIT_REV_DISPLAY})"
echo "  User:        admin"
if [[ -n "$ARGO_PASS" ]]; then
  echo "  Password:    ${ARGO_PASS}"
else
  echo "  Password:    kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
fi
echo ""
echo "  OriginHub API:  http://api.originhub.local${URL_SUFFIX}"
echo "  Grafana:        http://grafana.originhub.local${URL_SUFFIX}  (admin / admin, local profile)"
echo "  Prometheus:     cluster-internal originhub-prometheus:9090"
echo "  Git SSH:        git@127.0.0.1:30222  (NodePort, local profile)"
if [[ -n "$URL_SUFFIX" ]]; then
  echo ""
  echo "  Note: ingress uses host port ${HOST_HTTP_PORT} (80/443 were in use)."
fi
echo ""
echo "  Domain config:  deploy/helm/originhub/${VALUE_FILE}"
echo "    domain.apiHost      → backend ingress"
echo "    domain.frontendUrl  → Vercel / Cloudflare Pages (CORS + OAuth)"
echo ""
echo "  GitOps: push deploy/ changes to ${GIT_REV_DISPLAY} on ${GIT_REPO_DISPLAY} for Argo CD to pick them up."
echo ""
echo "  Teardown:       make k8s-purge"
echo "  kubectl:        make k8s-kubeconfig  (refresh after kind recreate)"
echo ""
