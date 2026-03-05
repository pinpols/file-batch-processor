#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[INFO] importing PostgreSQL test data"
"${SCRIPT_DIR}/load-testdata-postgres.sh"

echo "[OK] all selected test data imported."
