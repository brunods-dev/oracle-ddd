#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$SCRIPT_DIR/lib.sh"
load_env

require_cmd terraform

if [ -f "$GENERATED_DIR/kubeconfig" ] && command -v kubectl >/dev/null 2>&1; then
  export KUBECONFIG="$GENERATED_DIR/kubeconfig"
  kubectl delete namespace "${K8S_NAMESPACE:-copa-ticketing}" --ignore-not-found=true || true
fi

tf destroy -auto-approve
