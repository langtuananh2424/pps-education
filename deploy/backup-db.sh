#!/usr/bin/env bash
# Backup Postgres (staging + production) theo lich 3-2-1 - copy file nay thanh
# /opt/pps-education/backup-db.sh tren server (chmod +x, chown deploy:deploy).
# Chay tu dong qua systemd timer pps-db-backup.timer (xem deploy/systemd/ va
# deploy/README.md muc 11) - khong chay tay tru khi test.
#
# 3-2-1: 3 ban - 1 ban goc (DB dang chay) + 2 ban local (khong ma hoa, thu
# muc "daily/weekly/monthly" duoi backups/<stack>) + 1 ban mahoa GPG dong bo
# len Google Drive (remote rclone "gdrive"). Ma hoa RIENG ban cloud vi DB co
# du lieu ca nhan hoc sinh/phu huynh, khong day plaintext len Drive ca nhan.
#
# Neu chua cau hinh GPG passphrase / rclone remote, script VAN chay backup
# local binh thuong (chi bo qua buoc cloud + log canh bao) - khong bao gio de
# thieu cau hinh cloud lam mat luon backup local.
set -uo pipefail

BACKUP_ROOT=/opt/pps-education/backups
# ppsvn = website cong khai ppsvietnam.edu.vn (pps-center-main/docker-compose.server.yml,
# chuyen ve tu VPS 2026-09) - container/user/db khac 2 stack kia, xem stack_db_info().
STACKS=(staging production ppsvn)
KEEP_DAILY=7
KEEP_WEEKLY=4
KEEP_MONTHLY=6
GPG_PASSPHRASE_FILE=/opt/pps-education/backup.gpg-passphrase
RCLONE_REMOTE=gdrive
RCLONE_REMOTE_PATH="pps-education-backups"
LOG_FILE="$BACKUP_ROOT/backup.log"

mkdir -p "$BACKUP_ROOT"
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" | tee -a "$LOG_FILE"; }

FAILED=0

# Giu lai N file moi nhat trong 1 thu muc, xoa phan con lai.
keep_newest() {
  local dir="$1" n="$2"
  [ -d "$dir" ] || return 0
  ls -1t "$dir" 2>/dev/null | tail -n "+$((n + 1))" | while IFS= read -r f; do
    rm -f -- "$dir/$f"
  done
}

# In ra "container user db" cua tung stack.
stack_db_info() {
  case "$1" in
    ppsvn) echo "ppsvn-postgres-1 ppscenter ppscenter" ;;
    *)     echo "pps-$1-postgres-1 pps_app pps_education" ;;
  esac
}

for stack in "${STACKS[@]}"; do
  read -r container db_user db_name <<< "$(stack_db_info "$stack")"
  ts="$(date +%Y%m%d_%H%M%S)"
  dow="$(date +%u)"  # 1=Thu Hai .. 7=Chu Nhat
  dom="$(date +%d)"

  daily_dir="$BACKUP_ROOT/$stack/daily"
  weekly_dir="$BACKUP_ROOT/$stack/weekly"
  monthly_dir="$BACKUP_ROOT/$stack/monthly"
  mkdir -p "$daily_dir" "$weekly_dir" "$monthly_dir"

  fname="${stack}_${db_name}_${ts}.dump"
  dump_path="$daily_dir/$fname"

  log "[$stack] Bat dau pg_dump..."
  if docker exec "$container" pg_dump -U "$db_user" -Fc "$db_name" > "$dump_path.tmp" 2>>"$LOG_FILE"; then
    mv "$dump_path.tmp" "$dump_path"
    log "[$stack] pg_dump OK: $fname ($(du -h "$dump_path" | cut -f1))"
  else
    rm -f "$dump_path.tmp"
    log "[$stack] LOI pg_dump - container '$container' co dang chay khong? Bo qua stack nay."
    FAILED=1
    continue
  fi

  if [ "$dow" = "7" ]; then
    ln -f "$dump_path" "$weekly_dir/$fname"
    log "[$stack] Da promote sang weekly (Chu Nhat)"
  fi
  if [ "$dom" = "01" ]; then
    ln -f "$dump_path" "$monthly_dir/$fname"
    log "[$stack] Da promote sang monthly (ngay 01)"
  fi

  keep_newest "$daily_dir" "$KEEP_DAILY"
  keep_newest "$weekly_dir" "$KEEP_WEEKLY"
  keep_newest "$monthly_dir" "$KEEP_MONTHLY"

  if [ -f "$GPG_PASSPHRASE_FILE" ]; then
    enc_daily_dir="$BACKUP_ROOT/encrypted/$stack/daily"
    enc_weekly_dir="$BACKUP_ROOT/encrypted/$stack/weekly"
    enc_monthly_dir="$BACKUP_ROOT/encrypted/$stack/monthly"
    mkdir -p "$enc_daily_dir" "$enc_weekly_dir" "$enc_monthly_dir"
    enc_name="${fname}.gpg"
    if gpg --batch --yes --pinentry-mode loopback --passphrase-file "$GPG_PASSPHRASE_FILE" \
        -c -o "$enc_daily_dir/$enc_name" "$dump_path" 2>>"$LOG_FILE"; then
      [ "$dow" = "7" ] && ln -f "$enc_daily_dir/$enc_name" "$enc_weekly_dir/$enc_name"
      [ "$dom" = "01" ] && ln -f "$enc_daily_dir/$enc_name" "$enc_monthly_dir/$enc_name"
      keep_newest "$enc_daily_dir" "$KEEP_DAILY"
      keep_newest "$enc_weekly_dir" "$KEEP_WEEKLY"
      keep_newest "$enc_monthly_dir" "$KEEP_MONTHLY"
      log "[$stack] Ma hoa GPG OK: $enc_name"
    else
      log "[$stack] LOI ma hoa GPG"
      FAILED=1
    fi
  fi
done

if [ -f "$GPG_PASSPHRASE_FILE" ] && command -v rclone >/dev/null 2>&1 \
    && rclone listremotes 2>/dev/null | grep -q "^${RCLONE_REMOTE}:"; then
  log "Dong bo ban ma hoa len ${RCLONE_REMOTE}:${RCLONE_REMOTE_PATH} ..."
  if rclone sync "$BACKUP_ROOT/encrypted" "${RCLONE_REMOTE}:${RCLONE_REMOTE_PATH}" \
      --log-file="$LOG_FILE" --log-level INFO; then
    log "rclone sync OK"
  else
    log "LOI rclone sync - ban local van con, kiem tra cau hinh remote '${RCLONE_REMOTE}'"
    FAILED=1
  fi
else
  log "BO QUA rclone sync (chua co $GPG_PASSPHRASE_FILE hoac remote rclone '${RCLONE_REMOTE}') - moi chi co 2 ban local, CHUA dat du 3-2-1. Xem deploy/README.md muc 11."
fi

if [ "$FAILED" = "1" ]; then
  log "=== Backup KET THUC VOI LOI - xem chi tiet o tren / journalctl -u pps-db-backup.service ==="
  exit 1
fi
log "=== Backup hoan tat, khong loi ==="
exit 0
