#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="$INFRA_DIR/terraform"

. "$SCRIPT_DIR/lib.sh"

load_env

require_cmd terraform

if command -v mysql >/dev/null 2>&1; then
  MYSQL_CLIENT="mysql"
elif command -v mariadb >/dev/null 2>&1; then
  MYSQL_CLIENT="mariadb"
else
  echo "Required command not found: mysql or mariadb" >&2
  echo "Install MySQL client locally before running this script." >&2
  exit 1
fi

tf_output_raw() {
  terraform -chdir="$TERRAFORM_DIR" output -raw "$1"
}

SQL_FILE_PATH="${SQL_FILE_PATH:-$INFRA_DIR/../database/000_terraform_schema_views_user_only.sql}"

MYSQL_HOST="${MYSQL_HOST:-$(tf_output_raw mysql_public_ip)}"
MYSQL_PORT="${MYSQL_PORT:-$(tf_output_raw mysql_port)}"
MYSQL_IMPORT_USER="${MYSQL_IMPORT_USER:-${TF_VAR_mysql_admin_username:-admin}}"
MYSQL_IMPORT_PASSWORD="${MYSQL_IMPORT_PASSWORD:-${TF_VAR_mysql_admin_password:-}}"

APP_DB_USER="${APP_DB_USER:-app}"
APP_DB_PASS="${APP_DB_PASS:-CopaTicketing#2026_App!9Qx}"

if [ -z "$MYSQL_HOST" ]; then
  echo "MySQL public host is empty. Run ./scripts/01_apply_terraform.sh first." >&2
  exit 1
fi

if [ -z "$MYSQL_PORT" ]; then
  echo "MySQL port is empty. Run ./scripts/01_apply_terraform.sh first." >&2
  exit 1
fi

if [ -z "$MYSQL_IMPORT_USER" ]; then
  echo "MYSQL_IMPORT_USER is empty." >&2
  exit 1
fi

if [ -z "$MYSQL_IMPORT_PASSWORD" ]; then
  echo "MYSQL_IMPORT_PASSWORD is empty. Set TF_VAR_mysql_admin_password or MYSQL_IMPORT_PASSWORD in .env." >&2
  exit 1
fi

if [ ! -f "$SQL_FILE_PATH" ]; then
  echo "SQL file not found: $SQL_FILE_PATH" >&2
  exit 1
fi

echo "MySQL host:       $MYSQL_HOST"
echo "MySQL port:       $MYSQL_PORT"
echo "Import user:      $MYSQL_IMPORT_USER"
echo "SQL file:         $SQL_FILE_PATH"
echo

echo "Waiting for MySQL to accept connections..."

MAX_ATTEMPTS="${MYSQL_WAIT_ATTEMPTS:-90}"
SLEEP_SECONDS="${MYSQL_WAIT_SLEEP_SECONDS:-10}"

attempt=1
while true; do
  if MYSQL_PWD="$MYSQL_IMPORT_PASSWORD" "$MYSQL_CLIENT" \
    --protocol=TCP \
    --connect-timeout=5 \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_IMPORT_USER" \
    -e "SELECT 1;" >/dev/null 2>&1; then
    break
  fi

  if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
    echo "MySQL did not become reachable after $MAX_ATTEMPTS attempts." >&2
    echo "Check NLB health, DB System state, security rules, and admin credentials." >&2
    exit 1
  fi

  echo "Attempt $attempt/$MAX_ATTEMPTS failed. Retrying in ${SLEEP_SECONDS}s..."
  attempt=$((attempt + 1))
  sleep "$SLEEP_SECONDS"
done

echo "MySQL is reachable."
echo "Importing SQL..."

MYSQL_PWD="$MYSQL_IMPORT_PASSWORD" "$MYSQL_CLIENT" \
  --protocol=TCP \
  --default-character-set=utf8mb4 \
  --comments \
  -h "$MYSQL_HOST" \
  -P "$MYSQL_PORT" \
  -u "$MYSQL_IMPORT_USER" \
  < "$SQL_FILE_PATH"

echo "SQL import completed."

MYSQL_PUBLIC_JDBC_URL="$(tf_output_raw mysql_public_jdbc_url)"

cat > "$SCRIPT_DIR/db.env" <<EOF_DB_ENV
export DB_URL="$MYSQL_PUBLIC_JDBC_URL"
export DB_USER="$APP_DB_USER"
export DB_PASS='$APP_DB_PASS'
EOF_DB_ENV

echo
echo "Generated:"
echo "  $SCRIPT_DIR/db.env"
echo
echo "Use these values in the application deployment:"
cat "$SCRIPT_DIR/db.env"