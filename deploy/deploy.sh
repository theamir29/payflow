#!/usr/bin/env bash
# Rebuild and restart the API on the VPS from the latest commit on main.
# Run on the server: /root/payflow/src/deploy/deploy.sh
set -euo pipefail

SRC=/root/payflow/src
ENV_FILE=/root/payflow/payflow.env

git -C "$SRC" pull --ff-only
docker build -t payflow-api:latest "$SRC/backend"

docker rm -f payflow-api 2>/dev/null || true
# Host network: the app reaches PostgreSQL on 127.0.0.1 and binds to 127.0.0.1:$PORT, invisible from outside.
docker run -d --name payflow-api --restart unless-stopped --network host \
  --env-file "$ENV_FILE" payflow-api:latest

echo "Waiting for the health check..."
for _ in $(seq 1 30); do
  if curl -sf http://127.0.0.1:8095/actuator/health | grep -q '"UP"'; then
    echo "PayFlow is up"
    exit 0
  fi
  sleep 3
done
docker logs --tail 80 payflow-api
exit 1
