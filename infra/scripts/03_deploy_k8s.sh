#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$INFRA_DIR/kubernetes"
TERRAFORM_DIR="$INFRA_DIR/terraform"
GENERATED_DIR="${GENERATED_DIR:-$INFRA_DIR/generated}"
RENDERED_DIR="$GENERATED_DIR/k8s"
KUBECONFIG_FILE="$GENERATED_DIR/kubeconfig"

. "$SCRIPT_DIR/lib.sh"

load_env

mkdir -p "$GENERATED_DIR"
mkdir -p "$RENDERED_DIR"

require_cmd kubectl
require_cmd terraform
require_cmd envsubst
require_env OCIR_USERNAME
require_env OCIR_AUTH_TOKEN

tf_output_raw() {
  terraform -chdir="$TERRAFORM_DIR" output -raw "$1"
}

need_file() {
  local file="$1"

  if [ ! -f "$file" ]; then
    echo "Required file not found: $file" >&2
    exit 1
  fi
}

apply_if_exists() {
  local file="$1"

  if [ -f "$file" ]; then
    kubectl apply -f "$file"
  else
    echo "Skipping missing manifest: $file"
  fi
}

need_file "$K8S_DIR/namespace.yaml"
need_file "$K8S_DIR/secrets.yaml.tpl"
need_file "$K8S_DIR/backend.yaml.tpl"
need_file "$K8S_DIR/frontend.yaml.tpl"
need_file "$K8S_DIR/services.yaml"
need_file "$K8S_DIR/service-loadbalancer.yaml"

echo "Generating kubeconfig..."
tf_output_raw kubeconfig > "$KUBECONFIG_FILE"
chmod 600 "$KUBECONFIG_FILE" 2>/dev/null || true

export KUBECONFIG="$KUBECONFIG_FILE"

if [ -f "$GENERATED_DIR/images.env" ]; then
  . "$GENERATED_DIR/images.env"
elif [ -f "$SCRIPT_DIR/images.env" ]; then
  echo "Warning: images.env found inside scripts/. Move it to generated/images.env."
  . "$SCRIPT_DIR/images.env"
fi

require_env BACKEND_IMAGE
require_env FRONTEND_IMAGE
require_env BACKEND_PORT
require_env DB_URL
require_env DB_USER
require_env DB_PASS
require_env DB_POOL_SIZE
require_env ADMIN_USER
require_env ADMIN_PASS
require_env CUSTOMER_USER
require_env CUSTOMER_PASS
require_env OCI_GENAI_API_KEY
require_env OCI_GENAI_MODEL_ID
require_env FRONTEND_PORT
require_env BACKEND_URL
require_env BACKEND_CUSTOMER_USER
require_env BACKEND_CUSTOMER_PASS
require_env BACKEND_ADMIN_USER
require_env BACKEND_ADMIN_PASS
require_env K8S_NAMESPACE

echo "Using namespace:      $K8S_NAMESPACE"
echo "Using backend image:  $BACKEND_IMAGE"
echo "Using frontend image: $FRONTEND_IMAGE"

kubectl apply -f "$K8S_DIR/namespace.yaml"

OCIR_ENDPOINT="$(tf_output_raw ocir_endpoint)"

kubectl -n "$K8S_NAMESPACE" create secret docker-registry ocir-secret \
  --docker-server="$OCIR_ENDPOINT" \
  --docker-username="$OCIR_USERNAME" \
  --docker-password="$OCIR_AUTH_TOKEN" \
  --dry-run=client \
  -o yaml | kubectl apply -f -

envsubst ' ${BACKEND_PORT} ${DB_URL} ${DB_USER} ${DB_PASS} ${DB_POOL_SIZE} ${ADMIN_USER} ${ADMIN_PASS} ${CUSTOMER_USER} ${CUSTOMER_PASS} ${OCI_GENAI_API_KEY} ${OCI_GENAI_MODEL_ID} ${K8S_NAMESPACE} ${BACKEND_IMAGE} ${FRONTEND_IMAGE} ${IMAGE_TAG} ${FRONTEND_PORT} ${BACKEND_URL} ${BACKEND_CUSTOMER_USER} ${BACKEND_CUSTOMER_PASS} ${BACKEND_ADMIN_USER} ${BACKEND_ADMIN_PASS}' \
  < "$K8S_DIR/secrets.yaml.tpl" \
  > "$RENDERED_DIR/secrets.yaml"

envsubst ' ${BACKEND_PORT} ${DB_URL} ${DB_USER} ${DB_PASS} ${DB_POOL_SIZE} ${ADMIN_USER} ${ADMIN_PASS} ${CUSTOMER_USER} ${CUSTOMER_PASS} ${OCI_GENAI_API_KEY} ${OCI_GENAI_MODEL_ID} ${K8S_NAMESPACE} ${BACKEND_IMAGE} ${FRONTEND_IMAGE} ${IMAGE_TAG} ${FRONTEND_PORT} ${BACKEND_URL} ${BACKEND_CUSTOMER_USER} ${BACKEND_CUSTOMER_PASS} ${BACKEND_ADMIN_USER} ${BACKEND_ADMIN_PASS}' \
  < "$K8S_DIR/backend.yaml.tpl" \
  > "$RENDERED_DIR/backend.yaml"

envsubst ' ${BACKEND_PORT} ${DB_URL} ${DB_USER} ${DB_PASS} ${DB_POOL_SIZE} ${ADMIN_USER} ${ADMIN_PASS} ${CUSTOMER_USER} ${CUSTOMER_PASS} ${OCI_GENAI_API_KEY} ${OCI_GENAI_MODEL_ID} ${K8S_NAMESPACE} ${BACKEND_IMAGE} ${FRONTEND_IMAGE} ${IMAGE_TAG} ${FRONTEND_PORT} ${BACKEND_URL} ${BACKEND_CUSTOMER_USER} ${BACKEND_CUSTOMER_PASS} ${BACKEND_ADMIN_USER} ${BACKEND_ADMIN_PASS}' \
  < "$K8S_DIR/frontend.yaml.tpl" \
  > "$RENDERED_DIR/frontend.yaml"

kubectl apply -f "$RENDERED_DIR/secrets.yaml"

kubectl apply -f "$K8S_DIR/services.yaml"

diagnose_deployment() {
  local deployment_name="$1"
  local label_name="$2"

  echo
  echo "Deployment failed: $deployment_name"
  echo "Collecting diagnostics..."
  echo

  kubectl -n "$K8S_NAMESPACE" get deploy "$deployment_name" -o wide || true
  kubectl -n "$K8S_NAMESPACE" describe deploy "$deployment_name" || true

  echo
  echo "ReplicaSets and Pods:"
  kubectl -n "$K8S_NAMESPACE" get rs,pods -l "app.kubernetes.io/name=$label_name" -o wide || true

  echo
  echo "Recent events:"
  kubectl -n "$K8S_NAMESPACE" get events --sort-by=.lastTimestamp | tail -n 80 || true

  echo
  echo "Pod details and logs:"
  for pod in $(kubectl -n "$K8S_NAMESPACE" get pod -l "app.kubernetes.io/name=$label_name" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null); do
    echo
    echo "----- describe pod/$pod -----"
    kubectl -n "$K8S_NAMESPACE" describe pod "$pod" || true

    echo
    echo "----- logs pod/$pod -----"
    kubectl -n "$K8S_NAMESPACE" logs "$pod" --all-containers --tail=200 || true

    echo
    echo "----- previous logs pod/$pod -----"
    kubectl -n "$K8S_NAMESPACE" logs "$pod" --all-containers --previous --tail=200 || true
  done
}

rollout_or_diagnose() {
  local deployment_name="$1"
  local label_name="$2"
  local timeout="${ROLLOUT_TIMEOUT:-600s}"

  if ! kubectl -n "$K8S_NAMESPACE" rollout status "deployment/$deployment_name" --timeout="$timeout"; then
    diagnose_deployment "$deployment_name" "$label_name"
    exit 1
  fi
}

kubectl apply -f "$RENDERED_DIR/backend.yaml"
kubectl apply -f "$RENDERED_DIR/frontend.yaml"

if [ "${FORCE_ROLLOUT_RESTART:-true}" = "true" ]; then
  echo "Forcing a fresh rollout..."
  kubectl -n "$K8S_NAMESPACE" rollout restart deployment/copa-backend
  kubectl -n "$K8S_NAMESPACE" rollout restart deployment/copa-frontend
fi

echo "Waiting for application deployments..."
rollout_or_diagnose "copa-backend" "copa-backend"
rollout_or_diagnose "copa-frontend" "copa-frontend"

kubectl apply -f "$K8S_DIR/service-loadbalancer.yaml"

echo "Deployment complete. Public endpoint may take a few minutes:"
kubectl -n "$K8S_NAMESPACE" get svc copa-frontend-lb
