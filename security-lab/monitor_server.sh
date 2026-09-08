#!/usr/bin/env bash
# Giám sát server trong lúc chạy load test — bù các điểm mù của Cockpit.
#
# Cockpit xem RẤT tốt: CPU/RAM/disk/network mức HỆ ĐIỀU HÀNH, và có sẵn
# terminal + quản lý service. Nhưng đúng những thứ quyết định kết quả bài
# test này thì Cockpit KHÔNG thấy:
#   - Số connection Postgres đang active  → nút thắt HikariCP (mặc định 10)
#   - Tách tài nguyên staging vs production → 2 stack chạy CHUNG 1 server
#   - Trạng thái cloudflared tunnel        → toàn bộ traffic đi qua đây
#
# Script này lấy mẫu định kỳ 3 nhóm đó rồi ghi ra CSV, dùng mốc thời gian
# UTC để ghép được với summary/timestamp của k6.
#
# Chạy TRÊN SERVER (qua terminal của Cockpit hoặc SSH). Nên chạy trong tmux
# để không mất khi đóng tab trình duyệt:
#
#   tmux new -s monitor
#   sudo ./monitor_server.sh staging 5 | tee ~/loadtest-$(date +%Y%m%d-%H%M).csv
#   # Ctrl-B rồi D để thoát ra, `tmux attach -t monitor` để quay lại
#
# Tham số:  $1 = stack (staging|production, mặc định staging)
#           $2 = chu kỳ lấy mẫu tính bằng giây (mặc định 5)

set -uo pipefail

STACK="${1:-staging}"
INTERVAL="${2:-5}"
STACK_DIR="/opt/pps-education/${STACK}"

if [[ ! -d "$STACK_DIR" ]]; then
  echo "Không tìm thấy $STACK_DIR — kiểm tra lại tên stack (staging|production)." >&2
  exit 1
fi

# Không hard-code tên container (thật ra là `pps-staging-backend-1`), mà hỏi
# thẳng compose.
#
# LƯU Ý: KHÔNG truyền `-p` ở đây. File compose đã tự khai báo `name:
# pps-staging` / `name: pps-production`; truyền thêm `-p staging` sẽ GHI ĐÈ
# tên đó và compose không tìm thấy container nào đang chạy.
cid_of() {
  docker compose -f "${STACK_DIR}/docker-compose.yml" ps -q "$1" 2>/dev/null | head -1
}

PG_CID="$(cid_of postgres)"
BE_CID="$(cid_of backend)"

if [[ -z "$PG_CID" || -z "$BE_CID" ]]; then
  echo "Không lấy được container id (postgres='$PG_CID' backend='$BE_CID')." >&2
  echo "Thử: cd $STACK_DIR && sudo docker compose ps" >&2
  exit 1
fi

# Container của stack CÒN LẠI — theo dõi song song để trả lời câu hỏi quan
# trọng nhất về mặt vận hành: bắn tải vào staging có làm production chậm không.
OTHER_STACK=$([[ "$STACK" == "staging" ]] && echo production || echo staging)
OTHER_BE_CID="$(docker compose -f "/opt/pps-education/${OTHER_STACK}/docker-compose.yml" ps -q backend 2>/dev/null | head -1)"

echo "# stack=$STACK pg=${PG_CID:0:12} backend=${BE_CID:0:12} other(${OTHER_STACK})=${OTHER_BE_CID:0:12} interval=${INTERVAL}s" >&2
echo "# Ctrl-C để dừng." >&2

echo "ts_utc,load1,mem_used_pct,disk_root_pct,pg_total,pg_active,pg_idle,pg_idle_tx,pg_waiting,pg_max_conn,be_cpu_pct,be_mem_mb,be_pids,other_cpu_pct,tunnel_ok,http_502_1m"

# ── Truy vấn Postgres ────────────────────────────────────────────────────
# `waiting` = đang chờ khoá/IO (wait_event_type='Lock' hoặc 'IO'). Nếu con
# số này tăng vọt cùng lúc p95 của k6 tăng → nghẽn ở DB chứ không phải CPU.
PG_SQL="SELECT count(*) FILTER (WHERE true),
               count(*) FILTER (WHERE state='active'),
               count(*) FILTER (WHERE state='idle'),
               count(*) FILTER (WHERE state='idle in transaction'),
               count(*) FILTER (WHERE wait_event_type IN ('Lock','IO')),
               current_setting('max_connections')
        FROM pg_stat_activity WHERE datname='pps_education';"

sample_pg() {
  docker exec "$PG_CID" psql -U pps_app -d pps_education -At -F',' -c "$PG_SQL" 2>/dev/null \
    || echo ",,,,,"
}

# ── docker stats ─────────────────────────────────────────────────────────
# --no-stream lấy 1 mẫu rồi thoát. Gọi 1 lần cho CẢ 2 container để chỉ chịu
# 1 lần chi phí (mỗi lệnh docker stats mất ~1s khởi động).
sample_docker() {
  local ids="$1"
  docker stats --no-stream --format '{{.ID}},{{.CPUPerc}},{{.MemUsage}},{{.PIDs}}' $ids 2>/dev/null
}

while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  LOAD1="$(awk '{print $1}' /proc/loadavg)"
  MEM_PCT="$(free | awk '/^Mem:/ {printf "%.1f", $3/$2*100}')"
  DISK_PCT="$(df --output=pcent / | tail -1 | tr -dc '0-9')"

  IFS=',' read -r PG_TOTAL PG_ACTIVE PG_IDLE PG_IDLE_TX PG_WAIT PG_MAX <<<"$(sample_pg)"

  STATS="$(sample_docker "$BE_CID ${OTHER_BE_CID:-}")"
  BE_LINE="$(grep "^${BE_CID:0:12}" <<<"$STATS")"
  BE_CPU="$(cut -d, -f2 <<<"$BE_LINE" | tr -d '%')"
  BE_MEM="$(cut -d, -f3 <<<"$BE_LINE" | awk '{print $1}' \
            | sed 's/GiB/*1024/;s/MiB//;s/KiB/\/1024/' | bc -l 2>/dev/null | cut -d. -f1)"
  BE_PIDS="$(cut -d, -f4 <<<"$BE_LINE")"

  OTHER_CPU=""
  if [[ -n "${OTHER_BE_CID:-}" ]]; then
    OTHER_CPU="$(grep "^${OTHER_BE_CID:0:12}" <<<"$STATS" | cut -d, -f2 | tr -d '%')"
  fi

  # Tunnel còn sống không — nếu tunnel chết thì k6 sẽ báo lỗi hàng loạt mà
  # nguyên nhân KHÔNG nằm ở Spring Boot.
  TUNNEL_OK="$(systemctl is-active cloudflared 2>/dev/null || echo unknown)"

  # 502 trong 1 phút gần nhất từ Nginx = backend từ chối/không kịp nhận
  # connection. Đây là dấu hiệu sớm và rõ nhất của việc chạm trần.
  ERR_502="$(journalctl -u nginx --since '1 min ago' --no-pager 2>/dev/null | grep -c ' 502 ' || true)"
  if [[ -z "$ERR_502" ]]; then
    ERR_502="$(awk -v d="$(date -d '1 min ago' '+%d/%b/%Y:%H:%M')" '$0 ~ d && / 502 /' \
               /var/log/nginx/access.log 2>/dev/null | wc -l || echo 0)"
  fi

  echo "${TS},${LOAD1},${MEM_PCT},${DISK_PCT},${PG_TOTAL},${PG_ACTIVE},${PG_IDLE},${PG_IDLE_TX},${PG_WAIT},${PG_MAX},${BE_CPU},${BE_MEM},${BE_PIDS},${OTHER_CPU},${TUNNEL_OK},${ERR_502}"

  sleep "$INTERVAL"
done
