#!/usr/bin/env bash
# Backup file media (MinIO bucket pps-media) cua PRODUCTION - copy file nay
# thanh /opt/pps-education/backup-media.sh tren server (chmod 750, chown
# deploy:deploy). Chay tu dong qua systemd timer pps-media-backup.timer (xem
# deploy/systemd/ va deploy/README.md muc 11b) - chay tay chi khi test:
#   sudo systemctl start pps-media-backup.service
#
# Doc qua S3 API cua MinIO (KHONG copy tho /mnt/pps-production/media - do la
# dinh dang noi bo xl.meta cua MinIO, copy luc dang chay co the khong nhat
# quan). Ket qua la file thuong dung ten key -> xem truc tiep duoc, khoi phuc
# duoc vao MinIO/S3 bat ky bang "rclone copy" nguoc lai.
#
# - current/        : ban sao moi nhat cua bucket. KHONG BAO GIO xoa theo khi
#                     object bi xoa tren MinIO (xoa nham van con de lay lai).
# - changed/<ts>/   : ban cu cua object bi GHI DE (rclone --backup-dir), giu
#                     KEEP_CHANGED_DAYS ngay.
# - Laptop keo current/ ve qua SFTP (user chi-doc pps-backup-pull, group
#   READER_GROUP) roi ma hoa bang rclone crypt - xem deploy/laptop/tai-backup.cmd
#   va deploy/README.md muc 11b. Script cap quyen doc RIENG current/ cho group
#   nay sau moi lan chay (umask 077 ben duoi lam file moi chi deploy doc duoc).
# Tai khoan MinIO dung o day chi co quyen DOC bucket pps-media (khong dung
# root) - credentials trong CRED_FILE, xem README muc 11b.
set -uo pipefail
cd /

MEDIA_BACKUP_ROOT=${MEDIA_BACKUP_ROOT:-/mnt/pps-backup/media}
# LV rieng phai dang duoc mount - neu khong, ghi vao /mnt/... se ghi thang len
# root filesystem va lam day "/" (cung bay voi deploy/README.md muc 12).
# Dat REQUIRE_MOUNT="" de bo qua (chi khi test).
REQUIRE_MOUNT=${REQUIRE_MOUNT-/mnt/pps-backup}
CRED_FILE=${CRED_FILE:-/opt/pps-education/media-backup.env}
MINIO_ENDPOINT=${MINIO_ENDPOINT:-http://127.0.0.1:9000}
BUCKET=pps-media
KEEP_CHANGED_DAYS=90
READER_GROUP=pps-backup
MIN_FREE_MB=10240

LOG_FILE="$MEDIA_BACKUP_ROOT/backup-media.log"
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" | tee -a "$LOG_FILE"; }

if [ -n "$REQUIRE_MOUNT" ] && ! mountpoint -q "$REQUIRE_MOUNT"; then
  echo "LOI $REQUIRE_MOUNT chua duoc mount (LV backup) - huy de khong ghi len root filesystem." >&2
  exit 1
fi
command -v rclone > /dev/null || { echo "LOI chua cai rclone." >&2; exit 1; }
[ -r "$CRED_FILE" ] || { echo "LOI khong doc duoc $CRED_FILE (tai khoan MinIO chi-doc)." >&2; exit 1; }

umask 077
mkdir -p "$MEDIA_BACKUP_ROOT/current" "$MEDIA_BACKUP_ROOT/changed"

exec 9> "$MEDIA_BACKUP_ROOT/.backup-media.lock"
if ! flock -n 9; then
  log "Da co 1 tien trinh backup media khac dang chay - thoat."
  exit 1
fi

free_mb="$(df -Pm "$MEDIA_BACKUP_ROOT" | awk 'NR==2 {print $4}')"
if [ "${free_mb:-0}" -lt "$MIN_FREE_MB" ]; then
  log "LOI chi con ${free_mb}MB trong tren $MEDIA_BACKUP_ROOT (< ${MIN_FREE_MB}MB) - huy backup media."
  exit 1
fi

# Credentials qua bien moi truong (khong dat tren command line -> khong lo qua ps).
set -a
# shellcheck disable=SC1090
. "$CRED_FILE"
set +a
export RCLONE_S3_PROVIDER=Minio RCLONE_S3_ENDPOINT="$MINIO_ENDPOINT" RCLONE_S3_ENV_AUTH=false
[ -n "${RCLONE_S3_ACCESS_KEY_ID:-}" ] && [ -n "${RCLONE_S3_SECRET_ACCESS_KEY:-}" ] \
  || { log "LOI $CRED_FILE thieu RCLONE_S3_ACCESS_KEY_ID / RCLONE_S3_SECRET_ACCESS_KEY."; exit 1; }

ts="$(date +%Y%m%d_%H%M%S)"
FAILED=0

log "=== Bat dau backup media: bucket $BUCKET -> $MEDIA_BACKUP_ROOT/current ==="
if rclone copy ":s3:$BUCKET" "$MEDIA_BACKUP_ROOT/current" \
    --backup-dir "$MEDIA_BACKUP_ROOT/changed/$ts" \
    --fast-list --transfers 4 --checkers 8 \
    --log-file "$LOG_FILE" --log-level NOTICE --stats 0 --stats-log-level NOTICE; then
  log "rclone copy OK"
else
  log "LOI rclone copy - xem chi tiet o tren"
  FAILED=1
fi

# Doi chieu: moi object tren MinIO phai co trong current/ voi cung kich thuoc.
# --one-way: file chi con trong backup (da bi xoa tren MinIO) la chu dich, khong bao loi.
if [ "$FAILED" = "0" ]; then
  if rclone check ":s3:$BUCKET" "$MEDIA_BACKUP_ROOT/current" --one-way --size-only \
      --fast-list --log-file "$LOG_FILE" --log-level ERROR; then
    log "Doi chieu OK: moi object tren MinIO deu co trong backup"
  else
    log "LOI doi chieu: co object tren MinIO THIEU/LECH kich thuoc trong backup"
    FAILED=1
  fi
fi

# Don ban cu (object bi ghi de) qua KEEP_CHANGED_DAYS ngay + thu muc rong.
find "$MEDIA_BACKUP_ROOT/changed" -mindepth 1 -maxdepth 1 -type d -mtime "+$KEEP_CHANGED_DAYS" \
  -exec rm -rf {} + 2>>"$LOG_FILE"
find "$MEDIA_BACKUP_ROOT/changed" -mindepth 1 -maxdepth 1 -type d -empty -delete 2>>"$LOG_FILE"

# Cho group READER_GROUP DOC DUOC RIENG current/ (giong encrypted/ cua
# backup-db.sh). changed/ va log van chi deploy doc. deploy phai thuoc group
# nay thi moi chgrp duoc - loi o day chi canh bao, khong lam hong backup.
if getent group "$READER_GROUP" > /dev/null; then
  if chgrp "$READER_GROUP" "$MEDIA_BACKUP_ROOT" && chmod 0710 "$MEDIA_BACKUP_ROOT"       && chgrp -R "$READER_GROUP" "$MEDIA_BACKUP_ROOT/current"       && chmod -R g+rX,g-w "$MEDIA_BACKUP_ROOT/current"; then
    log "Da cap quyen doc current/ cho group $READER_GROUP (laptop keo ve qua SFTP)"
  else
    log "Canh bao: khong cap duoc quyen doc current/ cho group $READER_GROUP (deploy da thuoc group nay chua?)"
  fi
fi

count="$(find "$MEDIA_BACKUP_ROOT/current" -type f | wc -l)"
log "Backup media: $count file, $(du -sh "$MEDIA_BACKUP_ROOT/current" | cut -f1) (current) + $(du -sh "$MEDIA_BACKUP_ROOT/changed" | cut -f1) (changed), con trong: $(df -Ph "$MEDIA_BACKUP_ROOT" | awk 'NR==2 {print $4}')"

if [ "$FAILED" = "1" ]; then
  log "=== Backup media KET THUC VOI LOI - xem journalctl -u pps-media-backup.service ==="
  exit 1
fi
log "=== Backup media hoan tat, khong loi ==="
exit 0
