#!/usr/bin/env bash
# Remove everything installed by deploy/scripts/bootstrap.sh (idempotent, no-op if missing).
#
#   make k8s-purge
#   DELETE_KIND=0 make k8s-purge   # keep kind cluster, only uninstall Helm/namespaces
set -uo pipefail

CLUSTER="${KIND_CLUSTER_NAME:-originhub}"
NAMESPACE="${K8S_NAMESPACE:-originhub}"
RELEASE="${K8S_RELEASE:-originhub}"
DELETE_KIND="${DELETE_KIND:-1}"

run() {
  "$@" 2>/dev/null || true
}

remove_argocd_finalizers() {
  if ! command -v kubectl >/dev/null 2>&1; then
    return 0
  fi
  local apps
  apps="$(kubectl get applications.argoproj.io -n argocd -o name 2>/dev/null || true)"
  if [[ -n "$apps" ]]; then
    while IFS= read -r app; do
      [[ -z "$app" ]] && continue
      run kubectl patch -n argocd "$app" --type merge -p '{"metadata":{"finalizers":null}}'
    done <<< "$apps"
  fi
  local appprojects
  appprojects="$(kubectl get appprojects.argoproj.io -n argocd -o name 2>/dev/null || true)"
  if [[ -n "$appprojects" ]]; then
    while IFS= read -r proj; do
      [[ -z "$proj" ]] && continue
      run kubectl patch -n argocd "$proj" --type merge -p '{"metadata":{"finalizers":null}}'
    done <<< "$appprojects"
  fi
}

delete_namespace() {
  local ns="$1"
  run kubectl delete namespace "$ns" --ignore-not-found --timeout=120s
}

uninstall_helm() {
  local release="$1"
  local ns="$2"
  if command -v helm >/dev/null 2>&1; then
    run helm uninstall "$release" -n "$ns" --wait --timeout 5m
  fi
  delete_namespace "$ns"
}

echo "→ Removing OriginHub stack (namespace: ${NAMESPACE})..."
uninstall_helm "$RELEASE" "$NAMESPACE"

echo "→ Removing Argo CD..."
remove_argocd_finalizers
uninstall_helm "argocd" "argocd"

echo "→ Removing ingress-nginx..."
uninstall_helm "ingress-nginx" "ingress-nginx"

echo "→ Removing cert-manager..."
uninstall_helm "cert-manager" "cert-manager"

if command -v kubectl >/dev/null 2>&1; then
  echo "→ Removing cluster-scoped cert-manager resources..."
  run kubectl delete clusterissuer selfsigned-issuer --ignore-not-found
  crds="$(kubectl get crd -o name 2>/dev/null | grep 'cert-manager.io' || true)"
  if [[ -n "$crds" ]]; then
    while IFS= read -r crd; do
      [[ -z "$crd" ]] && continue
      run kubectl delete "$crd" --ignore-not-found --timeout=60s
    done <<< "$crds"
  fi
fi

if [[ "$DELETE_KIND" == "1" ]] && command -v kind >/dev/null 2>&1; then
  if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    echo "→ Deleting kind cluster '${CLUSTER}'..."
    run kind delete cluster --name "$CLUSTER"
  fi
fi

echo ""
echo "  K8s purge complete."
echo "  (Helm releases, namespaces, PVCs, kind cluster removed if they existed.)"
echo ""
