#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$SCRIPT_DIR/lib.sh"
load_env
ensure_generated_dir

require_cmd terraform
require_env TF_VAR_region
require_env TF_VAR_tenancy_ocid
require_env TF_VAR_compartment_ocid

cd "$PROJECT_ROOT"

tf init -upgrade
tf fmt -recursive
tf validate
tf apply -auto-approve

tf output -raw kubeconfig > "$GENERATED_DIR/kubeconfig"
chmod 600 "$GENERATED_DIR/kubeconfig"
tf output -json > "$GENERATED_DIR/tf-outputs.json"

echo "Terraform applied. Kubeconfig: $GENERATED_DIR/kubeconfig"
echo "Cluster: $(tf output -raw cluster_name)"
echo "OCIR backend: $(tf output -raw backend_repository_url)"
echo "OCIR frontend: $(tf output -raw frontend_repository_url)"
