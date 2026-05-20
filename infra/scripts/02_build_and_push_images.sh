#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"

. "$SCRIPT_DIR/lib.sh"

load_env
ensure_generated_dir

require_cmd terraform
require_cmd podman
require_cmd git
require_env OCIR_USERNAME
require_env OCIR_AUTH_TOKEN

APP_SRC_DIR="${APP_SRC_DIR:-$REPO_ROOT}"
BACKEND_DIR="${BACKEND_DIR:-$APP_SRC_DIR/backend}"
FRONTEND_DIR="${FRONTEND_DIR:-$APP_SRC_DIR/frontend}"

IMAGE_TAG="${IMAGE_TAG:-demo-$(date +%Y%m%d%H%M%S)}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-}"

TERRAFORM_DOCKER_PLATFORM="$(tf output -raw worker_node_container_platform)"
if [ -z "$DOCKER_PLATFORM" ]; then
  DOCKER_PLATFORM="$TERRAFORM_DOCKER_PLATFORM"
fi

OCIR_ENDPOINT="$(tf output -raw ocir_endpoint)"
BACKEND_REPO="$(tf output -raw backend_repository_url)"
FRONTEND_REPO="$(tf output -raw frontend_repository_url)"

BACKEND_IMAGE="$BACKEND_REPO:$IMAGE_TAG"
FRONTEND_IMAGE="$FRONTEND_REPO:$IMAGE_TAG"

if [ ! -d "$BACKEND_DIR" ]; then
  echo "Backend directory not found: $BACKEND_DIR" >&2
  exit 1
fi

if [ ! -f "$BACKEND_DIR/Dockerfile" ]; then
  echo "Backend Dockerfile not found: $BACKEND_DIR/Dockerfile" >&2
  exit 1
fi

if [ ! -d "$FRONTEND_DIR" ]; then
  echo "Frontend directory not found: $FRONTEND_DIR" >&2
  exit 1
fi

if [ ! -f "$FRONTEND_DIR/Dockerfile" ]; then
  echo "Frontend Dockerfile not found: $FRONTEND_DIR/Dockerfile" >&2
  exit 1
fi

echo "Using app source directory: $APP_SRC_DIR"
echo "Using backend directory:    $BACKEND_DIR"
echo "Using frontend directory:   $FRONTEND_DIR"
echo "Using Docker platform:      $DOCKER_PLATFORM"

printf '%s' "$OCIR_AUTH_TOKEN" | podman login "$OCIR_ENDPOINT" \
  --username "$OCIR_USERNAME" \
  --password-stdin

podman build \
  --platform "$DOCKER_PLATFORM" \
  -t "$BACKEND_IMAGE" \
  -f "$BACKEND_DIR/Dockerfile" \
  "$BACKEND_DIR"

podman push "$BACKEND_IMAGE"

podman build \
  --platform "$DOCKER_PLATFORM" \
  -t "$FRONTEND_IMAGE" \
  -f "$FRONTEND_DIR/Dockerfile" \
  "$FRONTEND_DIR"

podman push "$FRONTEND_IMAGE"

cat > "$SCRIPT_DIR/images.env" <<EOF_IMAGES
export BACKEND_IMAGE="$BACKEND_IMAGE"
export FRONTEND_IMAGE="$FRONTEND_IMAGE"
export IMAGE_TAG="$IMAGE_TAG"
EOF_IMAGES

echo "Images pushed:"
echo "  $BACKEND_IMAGE"
echo "  $FRONTEND_IMAGE"
echo
echo "Images env written to:"
echo "  $SCRIPT_DIR/images.env"