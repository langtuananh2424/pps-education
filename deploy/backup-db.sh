#!/usr/bin/env bash
# Backup Postgres (staging + production + ppsvn) theo quy tac 3-2-1 - copy file
# nay thanh /opt/pps-education/backup-db.sh tren server (chmod 750, chown
# deploy:deploy). Chay tu dong qua systemd timer pps-db-backup.timer (xem
# deploy/systemd/ va deploy/README.md muc 11) - chay tay chi khi test:
#   sudo systemctl start pps-db-backup.service
#
# 3-2-1: 1 ban goc (DB dang chay) + ban local (khong ma hoa, thu muc
# daily/weekly/monthly duoi backups/<stack>) + 1 ban ma hoa GPG dong bo len
# Google Drive (remote rclone "gdrive"). Ma hoa RIENG ban cloud vi DB co du
# lieu ca nhan hoc sinh/phu huynh, khong day plaintext len Drive ca nhan.
#
# Neu chua cau hinh GPG passphrase / rclone remote, script VAN chay backup
# local binh thuong (chi bo qua buoc cloud + log canh bao) - thieu cau hinh
# cloud khong bao gio duoc lam mat luon backup local.
set -uo pipefail

BACKUP_ROOT=${BACKUP_ROOT:-/opt/pps-education/backups}
# ppsvn = website cong khai ppsvietnam.edu.vn (repo ppsvn-web, /opt/pps-center,
# compose project "ppsvn") - container/user/db khac 2 stack kia, xem stack_db_info().
STACKS=(${STACKS:-staging production ppsvn})
KEEP_DAILY=7
KEEP_WEEKLY=4
KEEP_MONTHLY=6
# Dung luong trong toi thieu (MB) tren BACKUP_ROOT truoc khi dump - tranh dump
# do dang lam day o chung voi root filesystem.
MIN_FREE_MB=5120
GPG_PASSPHRASE_FILE=/opt/pps-education/backup.gpg-passphrase
RCLONE_REMOTE=gdrive
RCLONE_REMOTE_PATH="pps-education-backups"
LOG_FILE="$BACKUP_ROOT/backup.log"
LOCK_FILE="$BACKUP_ROOT/.backup.lock"

umask 077
mkdir -p "$BACKUP_ROOT"
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" | tee -a "$LOG_FILE"; }

# Khong cho 2 lan backup chay chong nhau (VD chay tay trung luc timer kich hoat).
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "Da co 1 tien trinh backup khac dang chay - thoat."
  exit 1
fi

FAILED=0

# Giu lai N file moi nhat (khop pattern, mac dinh moi file) trong 1 thu muc,
# xoa phan con lai.
keep_newest() {
  local dir="$1" n="$2" pattern="${3:-*}"
  [ -d "$dir" ] || return 0
  find "$dir" -maxdepth 1 -type f -name "$pattern" -printf '%T@ %p\n' | sort -rn \
    | tail -n "+$((n + 1))" | cut -d' ' -f2- | while IFS= read -r f; do
      rm -f -- "$f"
    done
}

# In ra "container user db" cua tung stack.
stack_db_info() {
  case "$1" in
    ppsvn) echo "ppsvn-postgres-1 ppscenter ppscenter" ;;
    *)     echo "pps-$1-postgres-1 pps_app pps_education" ;;
  esac
}

free_mb="$(df -Pm "$BACKUP_ROOT" | awk 'NR==2 {print $4}')"
if [ "${free_mb:-0}" -lt "$MIN_FREE_MB" ]; then
  log "LOI chi con ${free_mb}MB trong tren $BACKUP_ROOT (< ${MIN_FREE_MB}MB) - huy backup."
  exit 1
fi

log "=== Bat dau backup: ${STACKS[*]} ==="

ts="$(date +%Y%m%d_%H%M%S)"
dow="$(date +%u)"  # 1=Thu Hai .. 7=Chu Nhat
dom="$(date +%d)"

for stack in "${STACKS[@]}"; do
  read -r container db_user db_name <<< "$(stack_db_info "$stack")"

  if ! docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null | grep -q true; then
    log "[$stack] LOI container '$container' khong chay - bo qua stack nay."
    FAILED=1
    continue
  fi

  daily_dir="$BACKUP_ROOT/$stack/daily"
  weekly_dir="$BACKUP_ROOT/$stack/weekly"
  monthly_dir="$BACKUP_ROOT/$stack/monthly"
  mkdir -p "$daily_dir" "$weekly_dir" "$monthly_dir"

  fname="${stack}_${db_name}_${ts}.dump"
  dump_path="$daily_dir/$fname"

  log "[$stack] Bat dau pg_dump..."
  if ! docker exec "$container" pg_dump -U "$db_user" -Fc -Z 6 "$db_name" \
      > "$dump_path.tmp" 2>>"$LOG_FILE"; then
    rm -f "$dump_path.tmp"
    log "[$stack] LOI pg_dump - bo qua stack nay."
    FAILED=1
    continue
  fi

  # Kiem tra dump doc duoc (doc muc luc archive) truoc khi coi la ban hop le -
  # dump bi cat ngang van co the co exit code 0 neu pipe loi giua chung.
  if ! docker exec -i "$container" pg_restore -l < "$dump_path.tmp" > /dev/null 2>>"$LOG_FILE"; then
    rm -f "$dump_path.tmp"
    log "[$stack] LOI dump khong hop le (pg_restore -l that bai) - bo qua stack nay."
    FAILED=1
    continue
  fi
  mv "$dump_path.tmp" "$dump_path"
  sha256sum "$dump_path" | sed "s#  .*/#  #" > "$dump_path.sha256"
  log "[$stack] pg_dump OK: $fname ($(du -h "$dump_path" | cut -f1))"

  # Role/quyen cap cluster (pg_dump khong chua) - nho, luu kem de restore sang
  # server moi khong bi thieu role owner.
  docker exec "$container" pg_dumpall -U "$db_user" --globals-only \
    > "$daily_dir/${stack}_globals_${ts}.sql" 2>>"$LOG_FILE" \
    || log "[$stack] Canh bao: khong dump duoc globals (khong anh huong ban dump chinh)."

  # Weekly/monthly la hard link toi cung 1 file -> khong ton them dung luong,
  # va xoa ban daily cu khong lam mat ban weekly/monthly.
  if [ "$dow" = "7" ]; then
    ln -f "$dump_path" "$weekly_dir/$fname"
    log "[$stack] Da promote sang weekly (Chu Nhat)"
  fi
  if [ "$dom" = "01" ]; then
    ln -f "$dump_path" "$monthly_dir/$fname"
    log "[$stack] Da promote sang monthly (ngay 01)"
  fi

  keep_newest "$daily_dir" "$KEEP_DAILY" '*.dump'
  keep_newest "$daily_dir" "$KEEP_DAILY" '*.dump.sha256'
  keep_newest "$daily_dir" "$KEEP_DAILY" '*_globals_*.sql'
  keep_newest "$weekly_dir" "$KEEP_WEEKLY"
  keep_newest "$monthly_dir" "$KEEP_MONTHLY"

  if [ -f "$GPG_PASSPHRASE_FILE" ]; then
    enc_daily_dir="$BACKUP_ROOT/encrypted/$stack/daily"
    enc_weekly_dir="$BACKUP_ROOT/encrypted/$stack/weekly"
    enc_monthly_dir="$BACKUP_ROOT/encrypted/$stack/monthly"
    mkdir -p "$enc_daily_dir" "$enc_weekly_dir" "$enc_monthly_dir"
    enc_name="${fname}.gpg"
    if gpg --batch --yes --pinentry-mode loopback --passphrase-file "$GPG_PASSPHRASE_FILE" \
        --symmetric --cipher-algo AES256 -o "$enc_daily_dir/$enc_name" "$dump_path" 2>>"$LOG_FILE"; then
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

# Dung "rclone copy" + tu don ban cu theo tuoi, KHONG dung "rclone sync": neu
# thu muc local bi xoa nham/hong, sync se xoa theo luon ban tren Drive -> mat
# ca ban off-site dung vao luc can no nhat.
if [ -f "$GPG_PASSPHRASE_FILE" ] && command -v rclone >/dev/null 2>&1 \
    && rclone listremotes 2>/dev/null | grep -q "^${RCLONE_REMOTE}:"; then
  remote="${RCLONE_REMOTE}:${RCLONE_REMOTE_PATH}"
  log "Day ban ma hoa len $remote ..."
  if rclone copy "$BACKUP_ROOT/encrypted" "$remote" \
      --log-file="$LOG_FILE" --log-level NOTICE; then
    log "rclone copy OK"
    for stack in "${STACKS[@]}"; do
      rclone delete "$remote/$stack/daily"   --min-age "$((KEEP_DAILY + 1))d"        2>>"$LOG_FILE"
      rclone delete "$remote/$stack/weekly"  --min-age "$((KEEP_WEEKLY * 7 + 1))d"   2>>"$LOG_FILE"
      rclone delete "$remote/$stack/monthly" --min-age "$((KEEP_MONTHLY * 31 + 1))d" 2>>"$LOG_FILE"
    done
  else
    log "LOI rclone copy - ban local van con, kiem tra cau hinh remote '${RCLONE_REMOTE}'"
    FAILED=1
  fi
elif [ ! -f "$GPG_PASSPHRASE_FILE" ]; then
  log "BO QUA day len cloud (chua co $GPG_PASSPHRASE_FILE) - CHUA dat du 3-2-1. Xem deploy/README.md muc 11."
else
  log "BO QUA day len cloud (chua cai rclone hoac chua co remote '${RCLONE_REMOTE}') - CHUA dat du 3-2-1. Xem deploy/README.md muc 11."
fi

log "Dung luong backup: $(du -sh "$BACKUP_ROOT" | cut -f1), con trong: $(df -Ph "$BACKUP_ROOT" | awk 'NR==2 {print $4}')"

if [ "$FAILED" = "1" ]; then
  log "=== Backup KET THUC VOI LOI - xem chi tiet o tren / journalctl -u pps-db-backup.service ==="
  exit 1
fi
log "=== Backup hoan tat, khong loi ==="
exit 0
