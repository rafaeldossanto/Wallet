#!/usr/bin/env bash
# Backs up Wallet's Postgres: pg_dump through `docker compose exec`, compressed, into
# WALLET_BACKUP_DIR, keeping 14 days. With WALLET_BACKUP_S3_URI set and the AWS CLI installed, the
# copy also goes to S3.
#
# Runs from any folder (e.g. from systemd, see backup.service.example); whoever runs it must be
# able to use Docker. Restore: Docs/Arquitetura/Deploy.md.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
RETENTION_DAYS=14

# Reads a variable from .env without sourcing it (the file holds PEM keys and passwords with
# special characters). A variable already in the environment wins.
env_value() {
  local name="$1" value
  if [[ -n "${!name:-}" ]]; then
    printf '%s' "${!name}"
    return
  fi
  [[ -f "$ENV_FILE" ]] || return 0
  value="$(grep -E "^${name}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "$value"
}

backup_dir="$(env_value WALLET_BACKUP_DIR)"
s3_uri="$(env_value WALLET_BACKUP_S3_URI)"
if [[ -z "$backup_dir" ]]; then
  echo "WALLET_BACKUP_DIR não está definida (deploy/.env)." >&2
  exit 1
fi

mkdir -p "$backup_dir"
chmod 700 "$backup_dir"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$backup_dir/wallet-$stamp.sql.gz"
partial="$target.partial"
trap 'rm -f "$partial"' EXIT

# -T: no terminal, so it works under systemd. A plain SQL dump restores with psql.
docker compose --project-name wallet --file "$DEPLOY_DIR/compose.yaml" --env-file "$ENV_FILE" \
  exec -T postgres pg_dump --username wallet --dbname wallet --no-owner --no-privileges \
  | gzip --best > "$partial"

# A dump that does not decompress is not a backup.
gzip --test "$partial"
mv "$partial" "$target"
chmod 600 "$target"
echo "Backup gravado em $target ($(du -h "$target" | cut -f1))."

if [[ -n "$s3_uri" ]]; then
  if command -v aws > /dev/null 2>&1; then
    aws s3 cp --only-show-errors "$target" "${s3_uri%/}/$(basename "$target")"
    echo "Cópia enviada para ${s3_uri%/}/."
  else
    echo "WALLET_BACKUP_S3_URI está definida, mas a AWS CLI não está instalada: sem cópia no S3." >&2
  fi
fi

find "$backup_dir" -maxdepth 1 -type f -name 'wallet-*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete \
  | sed 's/^/Removido (mais de 14 dias): /'
