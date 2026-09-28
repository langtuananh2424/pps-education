#!/usr/bin/env bash
# Bao cao nhanh tinh trang backup tren server (CHI DOC, khong thay doi gi) -
# copy file nay thanh /opt/pps-education/check-backups.sh (chmod 750, chown
# deploy:deploy) roi chay:
#   sudo /opt/pps-education/check-backups.sh
#
# Kiem tra:
# - 2 timer pps-db-backup / pps-media-backup: dang bat, lan chay gan nhat +
#   ket qua, lan chay ke tiep.
# - DB (backup-db.sh): moi stack co ban daily moi (< MAX_AGE_H gio), ban ma hoa
#   .gpg tuong ung, so ban weekly/monthly/manual; ket qua lan chay cuoi trong
#   backup.log.
# - Media (backup-media.sh): LV /mnt/pps-backup dang mount, ket qua + tuoi lan
#   chay cuoi trong backup-media.log, so file/dung luong current/ va changed/.
# - Dung luong trong cac o lien quan.
#
# Exit 0 = khong co canh bao, 1 = co it nhat 1 canh bao (dong "[!!]") - dung
# duoc cho giam sat tu dong sau nay.
set -uo pipefail
cd /

BACKUP_ROOT=${BACKUP_ROOT:-/opt/pps-education/backups}
MEDIA_BACKUP_ROOT=${MEDIA_BACKUP_ROOT:-/mnt/pps-backup/media}
MEDIA_MOUNT=${MEDIA_MOUNT-/mnt/pps-backup}
STACKS=(${STACKS:-staging production ppsvn})
# Timer chay hang ngay (+ tre ngau nhien toi da 5 phut) -> qua 26h la da lo 1 lan.
MAX_AGE_H=${MAX_AGE_H:-26}
DISK_WARN_PCT=${DISK_WARN_PCT:-85}
DISKS=(${DISKS:-/ /mnt/pps-backup /mnt/pps-production/db /mnt/pps-production/media})

WARN=0
ok()   { printf '  [OK] %s\n' "$*"; }
warn() { printf '  [!!] %s\n' "$*"; WARN=$((WARN + 1)); }
info() { printf '       %s\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "Can chay bang sudo (thu muc backup chi deploy/root doc duoc): sudo $0" >&2
  exit 2
fi

now=$(date +%s)
# Tuoi (gio) cua 1 file theo mtime.
age_h() { echo $(( (now - $(stat -c %Y "$1")) / 3600 )); }
# File moi nhat khop pattern trong 1 thu muc (in duong dan, rong neu khong co).
newest() {
  [ -d "$1" ] || return 0
  find "$1" -maxdepth 1 -type f -name "$2" -printf '%T@ %p\n' 2>/dev/null \
    | sort -rn | head -n 1 | cut -d' ' -f2-
}
count() {
  [ -d "$1" ] || { echo 0; return; }
  find "$1" -maxdepth 1 -type f -name "$2" 2>/dev/null | wc -l
}
# Ket qua lan chay cuoi trong 1 log cua script backup: in "<dong ket thuc>|<so dong LOI>".
last_run() {
  local log="$1" start_pat="$2" end_pat="$3" start_line
  [ -r "$log" ] || return 0
  start_line=$(grep -n -- "$start_pat" "$log" | tail -n 1 | cut -d: -f1)
  [ -n "$start_line" ] || return 0
  tail -n "+$start_line" "$log" | awk -v e="$end_pat" '
    /LOI/ && $0 !~ e { loi++ }
    $0 ~ e { last = $0 }
    END { printf "%s|%d\n", last, loi + 0 }'
}

echo "=== Kiem tra backup - $(hostname) - $(date '+%Y-%m-%d %H:%M:%S') ==="

echo
echo "--- Timer systemd ---"
if command -v systemctl > /dev/null 2>&1; then
  for u in pps-db-backup pps-media-backup; do
    if systemctl is-active --quiet "$u.timer"; then
      last=$(systemctl show "$u.service" -p ExecMainStartTimestamp --value)
      result=$(systemctl show "$u.service" -p Result --value)
      next=$(systemctl show "$u.timer" -p NextElapseUSecRealtime --value)
      if [ -z "$last" ] || [ "$last" = "n/a" ]; then
        warn "$u: timer bat nhung CHUA chay lan nao (ke tiep: ${next:-?})"
      elif [ "$result" != "success" ]; then
        warn "$u: lan chay cuoi ${last} ket qua '${result}' - xem: journalctl -u $u.service -n 50"
      else
        ok "$u: lan cuoi ${last} (success), ke tiep ${next:-?}"
      fi
    else
      warn "$u.timer KHONG chay - bat lai: sudo systemctl enable --now $u.timer"
    fi
  done
else
  warn "khong co systemctl - bo qua kiem tra timer"
fi

echo
echo "--- Backup DB ($BACKUP_ROOT) ---"
r=$(last_run "$BACKUP_ROOT/backup.log" "=== Bat dau backup:" "=== Backup (hoan tat|KET THUC)")
if [ -z "$r" ]; then
  warn "chua co lan chay nao trong $BACKUP_ROOT/backup.log"
else
  end_line=${r%|*}; loi=${r##*|}
  if [ -z "$end_line" ]; then
    warn "lan chay cuoi chua ket thuc (dang chay hoac bi ngat) - xem $BACKUP_ROOT/backup.log"
  elif [ "$loi" -gt 0 ] || [[ "$end_line" == *"KET THUC VOI LOI"* ]]; then
    warn "lan chay cuoi co $loi dong LOI: ${end_line}"
  else
    ok "lan chay cuoi: ${end_line}"
  fi
fi
for s in "${STACKS[@]}"; do
  d="$BACKUP_ROOT/$s"
  if [ ! -d "$d/daily" ]; then
    warn "[$s] khong co thu muc $d/daily"
    continue
  fi
  f=$(newest "$d/daily" '*.dump')
  if [ -z "$f" ]; then
    warn "[$s] khong co ban .dump nao trong daily/"
    continue
  fi
  a=$(age_h "$f")
  line="[$s] moi nhat $(basename "$f") ($(du -h "$f" | cut -f1), ${a}h truoc) - daily $(count "$d/daily" '*.dump'), weekly $(count "$d/weekly" '*.dump'), monthly $(count "$d/monthly" '*.dump'), manual $(count "$d/manual" '*.dump')"
  if [ "$a" -gt "$MAX_AGE_H" ]; then warn "$line - QUA ${MAX_AGE_H}h"; else ok "$line"; fi
  if [ -d "$BACKUP_ROOT/encrypted/$s/daily" ]; then
    g=$(newest "$BACKUP_ROOT/encrypted/$s/daily" '*.gpg')
    if [ -z "$g" ]; then
      warn "[$s] khong co ban ma hoa .gpg (laptop khong co gi de keo)"
    elif [ "$(basename "$g")" != "$(basename "$f").gpg" ]; then
      warn "[$s] ban .gpg moi nhat ($(basename "$g")) khong khop ban .dump moi nhat - kiem tra buoc ma hoa GPG"
    fi
  fi
done

echo
echo "--- Backup media ($MEDIA_BACKUP_ROOT) ---"
if [ -n "$MEDIA_MOUNT" ] && ! mountpoint -q "$MEDIA_MOUNT"; then
  warn "$MEDIA_MOUNT CHUA mount - backup media se tu choi chay (xem README muc 11b)"
elif [ ! -d "$MEDIA_BACKUP_ROOT" ]; then
  warn "khong co thu muc $MEDIA_BACKUP_ROOT"
else
  mlog="$MEDIA_BACKUP_ROOT/backup-media.log"
  r=$(last_run "$mlog" "=== Bat dau backup media" "=== Backup media (hoan tat|KET THUC)")
  if [ -z "$r" ]; then
    warn "chua co lan chay nao trong $mlog"
  else
    end_line=${r%|*}; loi=${r##*|}
    a=$(age_h "$mlog")
    if [ -z "$end_line" ]; then
      warn "lan chay cuoi chua ket thuc (dang chay hoac bi ngat) - xem $mlog"
    elif [ "$loi" -gt 0 ] || [[ "$end_line" == *"KET THUC VOI LOI"* ]]; then
      warn "lan chay cuoi co $loi dong LOI: ${end_line}"
    elif [ "$a" -gt "$MAX_AGE_H" ]; then
      warn "lan chay cuoi da ${a}h truoc (qua ${MAX_AGE_H}h): ${end_line}"
    else
      ok "lan chay cuoi (${a}h truoc): ${end_line}"
    fi
    summary=$(grep -- "Backup media:" "$mlog" | tail -n 1)
    [ -n "$summary" ] && info "${summary}"
  fi
  if [ -d "$MEDIA_BACKUP_ROOT/current" ]; then
    info "current/: $(find "$MEDIA_BACKUP_ROOT/current" -type f | wc -l) file, $(du -sh "$MEDIA_BACKUP_ROOT/current" | cut -f1); changed/: $(find "$MEDIA_BACKUP_ROOT/changed" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l) lan ghi de, $(du -sh "$MEDIA_BACKUP_ROOT/changed" 2>/dev/null | cut -f1)"
  fi
  raw=$(find "$(dirname "$MEDIA_BACKUP_ROOT")" -maxdepth 1 -type d -name 'media-raw-*' 2>/dev/null | sort)
  [ -n "$raw" ] && info "ban copy tho media (khong tu xoa, runbook muc 5): $(echo "$raw" | xargs -n1 basename | tr '\n' ' ')"
fi

echo
echo "--- Dung luong o ---"
for m in "${DISKS[@]}"; do
  [ -d "$m" ] || continue
  read -r pct avail < <(df --output=pcent,avail -h "$m" | awk 'NR==2 {gsub("%","",$1); print $1, $2}')
  line="$m: dung ${pct}%, con trong ${avail}"
  if ! [[ "$pct" =~ ^[0-9]+$ ]]; then warn "$m: khong doc duoc dung luong (df)"; continue; fi
  if [ "$pct" -ge "$DISK_WARN_PCT" ]; then warn "$line - tren ${DISK_WARN_PCT}%"; else ok "$line"; fi
done

echo
if [ "$WARN" -eq 0 ]; then
  echo "=== KET QUA: OK - khong co canh bao ==="
  exit 0
fi
echo "=== KET QUA: $WARN canh bao - xem cac dong [!!] ==="
exit 1
