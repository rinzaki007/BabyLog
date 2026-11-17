#!/bin/sh
set -eu

# Repair ownership once for existing NAS bind mounts, then run unprivileged.
if [ ! -f /data/.babylog-permissions-ready ]; then
    chown -R 10001:10001 /data
    gosu 10001:10001 touch /data/.babylog-permissions-ready
fi

exec gosu 10001:10001 sh -c 'mkdir -p /data/photos /data/backups && exec uvicorn app.main:app --host 0.0.0.0 --port 18765 --proxy-headers --forwarded-allow-ips="${BABYLOG_TRUSTED_PROXY_IPS:-127.0.0.1}"'
