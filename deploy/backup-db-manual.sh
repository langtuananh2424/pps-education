#!/usr/bin/env bash
# Backup THU CONG 1 DB truoc thay doi lon (merge PR co migration Flyway nguy
# hiem, chay SQL sua du lieu tay, import hang loat...) - copy file nay thanh
# /opt/pps-education/backup-db-manual.sh tren server (chmod 750, chown
# deploy:deploy). Quy trinh day du: deploy/RUNBOOK-db-backup-restore.md.
#
#   sudo -u deploy /opt/pps-education/backup-db-manual.sh production truoc-V193-doi-cot-hoc-phi
#
# Khac backup-db.sh (tu dong hang ngay):
# - Ghi vao backups/<stack>/manual/ - KHONG bi xoay vong xoa tu dong, ban
#   "truoc thay doi" khong bi day ra khoi 7 ban daily. Tu xoa khi khong can nua.
# - Chi chay 1 stack, ten file kem nhan (label) de biet ban nay chup truoc viec gi.
# - Ghi kem phien ban Flyway hien tai (.flyway.txt) - can biet khi rollback
#   image backend ve dung ban tuong ung voi schema nay.
set -euo pipefail

BACKUP_ROOT=${BACKUP_ROOT:-/opt/pps-education/backups}

usage() {
  echo "Cach dung: $0 <staging|production|ppsvn> <nhan-ngan-khong-dau-cach>" >&2
  echo "  VD: $0 production truoc-V193-doi-cot-hoc-phi" >&2
  exit 2
}

[ $# -eq 2 ] || usage
stack="$1"
# Chi giu ky tu an toan cho ten file (dau tieng Viet/khoang trang -> "-").
label="$(printf '%s' "$2" | tr -c 'A-Za-z0-9._-' '-' | tr -s '-' | sed 's/^-//; s/-$//')"
[ -n "$label" ] || usage

case "$stack" in
  ppsvn)              container=ppsvn-postgres-1;        db_user=ppscenter; db_name=ppscenter;     backend=ppsvn-backend-1 ;;
  staging|production) container="pps-$stack-postgres-1"; db_user=pps_app;   db_name=pps_education; backend="pps-$stack-backend-1" ;;
  *) usage ;;
esac
ts="$(date +%Y%m%d_%H%M%S)"

docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null | grep -q true \
  || { echo "LOI container '$container' khong chay." >&2; exit 1; }

umask 077
dir="$BACKUP_ROOT/$stack/manual"
mkdir -p "$dir"
base="$dir/${stack}_${db_name}_${ts}_${label}"

echo "pg_dump $stack -> $base.dump ..."
docker exec "$container" pg_dump -U "$db_user" -Fc -Z 6 "$db_name" > "$base.dump.tmp"
docker exec -i "$container" pg_restore -l < "$base.dump.tmp" > /dev/null \
  || { rm -f "$base.dump.tmp"; echo "LOI dump khong hop le." >&2; exit 1; }
mv "$base.dump.tmp" "$base.dump"
sha256sum "$base.dump" | sed "s#  .*/#  #" > "$base.dump.sha256"

# Giu lai image backend dang chay bang 1 tag local rieng: CD chay
# "docker image prune -f" sau moi lan deploy -> image cu mat tag se bi xoa,
# luc can rollback migration hong se khong con dung image khop voi schema nay.
rollback_image=""
if image_id="$(docker inspect -f '{{.Image}}' "$backend" 2>/dev/null)"; then
  rollback_image="pps-rollback/${stack}-backend:$(echo "${ts}-${label}" | tr 'A-Z' 'a-z' | cut -c1-120)"
  docker tag "$image_id" "$rollback_image"
fi

{
  echo "stack:          $stack"
  echo "thoi diem:      $ts"
  echo "nhan:           $label"
  echo "backend image:  ${rollback_image:-(khong tim thay container $backend)}"
  # ppsvn dung Prisma (khong co flyway_schema_history) -> bo qua.
  if [ "$stack" != "ppsvn" ]; then
    echo
    echo "Flyway (5 migration gan nhat):"
    docker exec "$container" psql -U "$db_user" -d "$db_name" -c \
      "SELECT version, description, installed_on FROM flyway_schema_history
       WHERE success ORDER BY installed_rank DESC LIMIT 5;" 2>&1 || true
  fi
} > "$base.info.txt"
cat "$base.info.txt"

echo
echo "OK: $base.dump ($(du -h "$base.dump" | cut -f1))"
echo "Khoi phuc (test vao DB scratch):  /opt/pps-education/restore-db.sh $stack $base.dump"
echo "Khoi phuc de DB that:             /opt/pps-education/restore-db.sh $stack $base.dump --live"
echo "Luu y: ban manual KHONG tu day len Drive va KHONG tu xoa - don dep: ls -lh $dir"
[ -n "$rollback_image" ] && echo "       image rollback giu lai toi khi xoa tay: docker rmi $rollback_image"
exit 0
