#!/usr/bin/env bash
set -euo pipefail

APP_USER=${APP_USER:-filebatch}
APP_GROUP=${APP_GROUP:-filebatch}
APP_HOME=${APP_HOME:-/opt/file-batch-processor}
SYSTEMD_DIR=${SYSTEMD_DIR:-/etc/systemd/system}
ENV_DIR=${ENV_DIR:-/etc/file-batch-processor}

if [ ! -f "$APP_HOME/file-batch-processor.jar" ]; then
  echo "Jar not found: $APP_HOME/file-batch-processor.jar" >&2
  echo "Please copy target/*.jar to this path first." >&2
  exit 1
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
  sudo useradd --system --home "$APP_HOME" --shell /usr/sbin/nologin "$APP_USER"
fi

sudo mkdir -p "$APP_HOME" "$ENV_DIR"
sudo chown -R "$APP_USER:$APP_GROUP" "$APP_HOME"

sudo cp deploy/systemd/file-batch-processor.service "$SYSTEMD_DIR/"
if [ ! -f "$ENV_DIR/file-batch-processor.env" ]; then
  sudo cp deploy/systemd/file-batch-processor.env.example "$ENV_DIR/file-batch-processor.env"
fi

sudo systemctl daemon-reload
sudo systemctl enable file-batch-processor.service

echo "Installed systemd unit."
echo "Edit env file: $ENV_DIR/file-batch-processor.env"
echo "Start service: sudo systemctl start file-batch-processor"
echo "Status: sudo systemctl status file-batch-processor"
echo "Note: first start will run Flyway migrations (including Quartz tables in V1_20__quartz_postgresql_tables.sql)."
