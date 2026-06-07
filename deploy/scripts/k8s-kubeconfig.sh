#!/usr/bin/env bash
# Refresh ~/.kube/config for the local kind cluster (API port changes after recreate).
set -euo pipefail

CLUSTER="${KIND_CLUSTER_NAME:-originhub}"

if ! command -v kind >/dev/null 2>&1; then
  echo "Missing: kind"
  exit 1
fi
if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "Kind cluster '${CLUSTER}' not found. Run: make k8s-bootstrap LOCAL=1"
  exit 1
fi

kind export kubeconfig --name "$CLUSTER"
kubectl config use-context "kind-${CLUSTER}" >/dev/null
kubectl cluster-info --request-timeout=10s
echo ""
echo "  context: kind-${CLUSTER}"
echo "  try:     kubectl get pods -n originhub"
