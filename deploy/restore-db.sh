#!/usr/bin/env bash
# Restore 1 ban backup do backup-db.sh tao ra - copy file nay thanh
# /opt/pps-education/restore-db.sh tren server (chmod 750, chown deploy:deploy).
#
# Mac dinh restore vao 1 DB SCRATCH moi (khong dung DB dang phuc vu) - dung
# cho test restore dinh ky hang quy va de tra cuu/lay lai du lieu cu:
#   ./restore-db.sh staging /opt/pps-education/backups/staging/daily/staging_pps_education_20260923_023000.dump
#   ./restore-db.sh production ~/production_pps_education_20260901_023000.dump.gpg   # ban tai tu Drive
#
# Ghi de DB THAT (khi su co that) - phai them --live va go lai ten stack de
# xac nhan; script tu dump 1 ban "pre-restore" cua DB hien tai truoc khi xoa:
#   ./restore-db.sh production <file> --live
set -euo pipefail

GPG_PASSPHRASE_FILE=/opt/pps-education/backup.gpg-passphrase
BACKUP_ROOT=${BACKUP_ROOT:-/opt/pps-education/backups}

usage() {
  echo "Cach dung: $0 <staging|production|ppsvn> <file.dump|file.dump.gpg> [--live]" >&2
  exit 2
}

[ $# -ge 2 ] || usage
stack="$1"; src="$2"; mode="${3:-scratch}"
[ "$mode" = "scratch" ] || [ "$mode" = "--live" ] || usage
[ -f "$src" ] || { echo "Khong tim thay file: $src" >&2; exit 1; }

case "$stack" in
  ppsvn)              container=ppsvn-postgres-1;         db_user=ppscenter; db_name=ppscenter;     backend=ppsvn-backend-1 ;;
  staging|production) container="pps-$stack-postgres-1";  db_user=pps_app;   db_name=pps_education; backend="pps-$stack-backend-1" ;;
  *) usage ;;
esac

psql_c() { docker exec -i "$container" psql -U "$db_user" -d postgres -v ON_ERROR_STOP=1 -qAtc "$1"; }

tmp_dump=""
cleanup() { if [ -n "$tmp_dump" ]; then rm -f "$tmp_dump"; fi; }
trap cleanup EXIT

# Giai ma neu la ban .gpg (ban tu Drive / thu muc encrypted).
dump="$src"
if [[ "$src" == *.gpg ]]; then
  [ -f "$GPG_PASSPHRASE_FILE" ] || { echo "Thieu $GPG_PASSPHRASE_FILE de giai ma." >&2; exit 1; }
  tmp_dump="$(mktemp --suffix=.dump)"
  echo "Giai ma $src ..."
  gpg --batch --yes --pinentry-mode loopback --passphrase-file "$GPG_PASSPHRASE_FILE" \
    -d -o "$tmp_dump" "$src"
  dump="$tmp_dump"
fi

# Doi chieu checksum neu co file .sha256 di kem (ban local).
if [ -f "$src.sha256" ]; then
  expected="$(cut -d' ' -f1 "$src.sha256")"
  actual="$(sha256sum "$src" | cut -d' ' -f1)"
  [ "$expected" = "$actual" ] || { echo "LOI checksum khong khop - file backup co the bi hong." >&2; exit 1; }
  echo "Checksum OK."
fi

docker exec -i "$container" pg_restore -l < "$dump" > /dev/null \
  || { echo "LOI file khong phai archive pg_dump hop le." >&2; exit 1; }

if [ "$mode" = "scratch" ]; then
  target="${db_name}_restore_$(date +%Y%m%d_%H%M%S)"
  echo "Tao DB scratch '$target' trong container $container ..."
  psql_c "CREATE DATABASE \"$target\" OWNER \"$db_user\";"
  docker exec -i "$container" pg_restore -U "$db_user" -d "$target" --no-owner --exit-on-error < "$dump"
  echo
  echo "Restore OK vao DB scratch '$target'. Kiem tra nhanh:"
  docker exec "$container" psql -U "$db_user" -d "$target" -c \
    "SELECT relname AS bang, n_live_tup AS so_dong FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10;"
  echo "Xong viec thi xoa DB scratch:"
  echo "  docker exec $container psql -U $db_user -d postgres -c 'DROP DATABASE \"$target\";'"
  exit 0
fi

# ---- --live: ghi de DB dang phuc vu ----
echo "!!! CANH BAO: se XOA TOAN BO DB '$db_name' cua stack '$stack' va thay bang:"
echo "    $src"
read -r -p "Go lai ten stack ('$stack') de xac nhan: " confirm
[ "$confirm" = "$stack" ] || { echo "Huy."; exit 1; }

pre_dir="$BACKUP_ROOT/$stack/pre-restore"
mkdir -p "$pre_dir"
pre="$pre_dir/${stack}_${db_name}_prerestore_$(date +%Y%m%d_%H%M%S).dump"
echo "Dump ban hien tai truoc khi ghi de -> $pre"
docker exec "$container" pg_dump -U "$db_user" -Fc "$db_name" > "$pre"

backend_was_running=0
if docker inspect -f '{{.State.Running}}' "$backend" 2>/dev/null | grep -q true; then
  backend_was_running=1
  echo "Dung backend $backend (tranh ghi vao DB giua chung) ..."
  docker stop "$backend" > /dev/null
fi

psql_c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$db_name' AND pid <> pg_backend_pid();" > /dev/null
psql_c "DROP DATABASE \"$db_name\";"
psql_c "CREATE DATABASE \"$db_name\" OWNER \"$db_user\";"
echo "pg_restore vao '$db_name' ..."
if ! docker exec -i "$container" pg_restore -U "$db_user" -d "$db_name" --exit-on-error < "$dump"; then
  echo "LOI pg_restore. DB dang o trang thai DO DANG - restore lai ban pre-restore:" >&2
  echo "  $0 $stack $pre --live" >&2
  exit 1
fi

if [ "$backend_was_running" = "1" ]; then
  echo "Khoi dong lai backend $backend ..."
  docker start "$backend" > /dev/null
fi
echo "Restore LIVE hoan tat. Ban truoc khi restore giu tai: $pre"
