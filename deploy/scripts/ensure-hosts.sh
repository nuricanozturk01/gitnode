#!/usr/bin/env bash
# Idempotent /etc/hosts block for OriginHub local kind (macOS-safe: use .test, not .local).
set -euo pipefail

MARKER="originhub-k8s-local"

k8s_flag_on() {
  case "${1:-0}" in
    1 | true | TRUE | yes | YES) return 0 ;;
    *) return 1 ;;
  esac
}

collect_hosts() {
  local domain="${K8S_DOMAIN:-originhub.test}"
  printf '%s\n' "${K8S_API_HOST:-api.${domain}}"
  printf '%s\n' "${K8S_ARGOCD_HOST:-argocd.${domain}}"
  if k8s_flag_on "${K8S_FRONTEND:-1}"; then
    printf '%s\n' "${K8S_FRONTEND_HOST:-app.${domain}}"
  fi
  if k8s_flag_on "${K8S_ADMIN_PANEL:-1}"; then
    printf '%s\n' "${K8S_ADMIN_PANEL_HOST:-admin.${domain}}"
  fi
  if k8s_flag_on "${K8S_OBSERVABILITY:-1}"; then
    printf '%s\n' "${K8S_GRAFANA_HOST:-grafana.${domain}}"
  fi
  if k8s_flag_on "${K8S_PROMETHEUS_INGRESS:-0}"; then
    printf '%s\n' "${K8S_PROMETHEUS_HOST:-prometheus.${domain}}"
  fi
}

ensure_hosts() {
  local hosts_line tmp host entry
  hosts_line="127.0.0.1"
  while IFS= read -r host; do
    [[ -n "$host" ]] && hosts_line+="  ${host}"
  done < <(collect_hosts)

  echo "→ Updating /etc/hosts (sudo required)..."
  tmp="$(mktemp)"
  if [[ -f /etc/hosts ]]; then
    grep -v "$MARKER" /etc/hosts \
      | grep -vE 'originhub\.(local|test)' \
      >"$tmp" || true
  fi
  {
    cat "$tmp"
    echo "# ${MARKER}"
    echo "$hosts_line"
  } | sudo tee /etc/hosts >/dev/null
  rm -f "$tmp"

  if [[ "$(uname -s)" == "Darwin" ]]; then
    dscacheutil -flushcache 2>/dev/null || true
    killall -HUP mDNSResponder 2>/dev/null || true
  fi

  echo "  ✓ /etc/hosts:"
  echo "    $hosts_line"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  ensure_hosts
fi
