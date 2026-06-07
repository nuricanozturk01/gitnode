#!/usr/bin/env bash
# Generate kind cluster config with configurable host port mappings.
# Usage: generate-kind-config.sh <httpHostPort> <httpsHostPort> <outputFile>
set -euo pipefail

HTTP_PORT="${1:?http host port}"
HTTPS_PORT="${2:?https host port}"
OUT="${3:?output file}"

cat >"$OUT" <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: ${HTTP_PORT}
        protocol: TCP
      - containerPort: 443
        hostPort: ${HTTPS_PORT}
        protocol: TCP
      - containerPort: 30222
        hostPort: 30222
        protocol: TCP
EOF
