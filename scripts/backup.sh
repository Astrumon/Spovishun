#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$PROJECT_DIR/backups"
LOG_FILE="$BACKUP_DIR/backup.log"
RETENTION_DAYS=14

mkdir -p "$BACKUP_DIR"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

# Load env vars (POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD)
if [ -f "$PROJECT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$PROJECT_DIR/.env"
  set +a
fi

POSTGRES_DB="${POSTGRES_DB:-spovishun_prod}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
TIMESTAMP="$(date '+%Y%m%d_%H%M%S')"
DUMP_FILE="$BACKUP_DIR/spovishun_${TIMESTAMP}.dump.gz"

log "Starting backup: db=$POSTGRES_DB user=$POSTGRES_USER"

if ! docker exec spovishun-db pg_dump -U "$POSTGRES_USER" -F c "$POSTGRES_DB" | gzip > "$DUMP_FILE"; then
  log "ERROR: pg_dump failed"
  exit 1
fi

log "Backup written: $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"

# Rotate: remove backups older than RETENTION_DAYS
find "$BACKUP_DIR" -name "spovishun_*.dump.gz" -mtime "+$RETENTION_DAYS" -delete
log "Retention cleanup done (>${RETENTION_DAYS} days removed)"
