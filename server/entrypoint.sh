#!/bin/sh
set -eu
mkdir -p /data/photos /data/backups
chown 10001:10001 /data /data/photos /data/backups
if [ -f /data/babylog.db ]; then chown 10001:10001 /data/babylog.db /data/babylog.db-wal /data/babylog.db-shm 2>/dev/null || true; fi
exec gosu 10001:10001 uvicorn app.main:app --host 0.0.0.0 --port 18765 --proxy-headers --forwarded-allow-ips=*
