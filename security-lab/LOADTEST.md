# Load test — đo năng lực hệ thống PPS Education

Hướng dẫn để **bạn tự chạy** trên hạ tầng của mình. Đây là đo hiệu năng, không
phải tấn công — chỉ nhắm vào staging/production của chính bạn, từ máy do bạn
kiểm soát, đã báo trước cho người liên quan.

## Ba script trong thư mục này

| File | Dùng khi |
|---|---|
| `loadtest_login_flow.js` | Chỉ đo riêng luồng auth (login→me→refresh→logout). Nhẹ, chạy nhanh, tốt để smoke test. |
| `loadtest_api_suite.js` | Đo diện rộng nhiều phân hệ, có 2 chế độ (xem dưới). Đây là script chính. |
| `monitor_server.sh` | Chạy **trên server** song song lúc test, ghi CSV các chỉ số Cockpit không thấy. |
| `run_loadtest.bat` | Chạy từ **máy Windows** — bọc sẵn mọi tham số, không phải nhớ cú pháp `-e`. |

## Chạy nhanh từ Windows

```bat
cd security-lab
run_loadtest.bat smoke        :: kiểm tra đăng nhập/endpoint trước, ~10 giây
run_loadtest.bat capacity     :: tìm trần (~17 phút)
run_loadtest.bat browse       :: mô phỏng người dùng thật
run_loadtest.bat login        :: chỉ luồng auth
```

Script tự hỏi tài khoản/mật khẩu (mật khẩu nhập ẩn), hoặc đọc từ biến môi
trường `TEST_USERNAME`/`TEST_PASSWORD` nếu đã đặt sẵn. Kết quả lưu vào
`security-lab/results/` (đã gitignore).

Đổi tham số bằng biến môi trường trước khi chạy:

```bat
set TARGET_URL=http://localhost:8080
set RPS_PEAK=1500
set SKIP_WRITES=1
run_loadtest.bat capacity
```

> **Lưu ý cú pháp:** các lệnh `TARGET_URL=... k6 run` ở phần dưới tài liệu này
> là cú pháp bash (dùng khi chạy trên server Linux). Trong PowerShell/cmd
> chúng **không chạy** — dùng `run_loadtest.bat`, hoặc cờ `k6 run -e KEY=VALUE`.

## Chọn đúng phép đo

Hai câu hỏi khác nhau, phải đo bằng hai cách khác nhau — trộn vào nhau sẽ ra
kết luận sai:

| Muốn biết | Chạy k6 ở đâu | TARGET_URL |
|---|---|---|
| **App chịu được bao nhiêu** (Spring Boot + Postgres) | Trên chính server | `http://localhost:8081` |
| **Đường truyền đưa vào được bao nhiêu** (Cloudflare Tunnel + Nginx) | Máy cá nhân | `https://admin-staging.ppsvietnam.edu.vn` |

Chạy k6 từ máy Windows cá nhân qua tunnel để đo *app* là sai: bạn sẽ đo giới
hạn của laptop và của tunnel, không phải của server 40 core.

## Ba chế độ của `loadtest_api_suite.js`

- **`MODE=smoke`** — 1 VU, 1 vòng. Kiểm tra đăng nhập được không, harvest được
  id không, endpoint có trả 2xx không. Luôn chạy cái này trước bài dài.
- **`MODE=browse`** (mặc định) — `ramping-vus`, mô phỏng người dùng thật có
  think-time. Trả lời "với N người dùng đồng thời thì trải nghiệm thế nào".
- **`MODE=capacity`** — `ramping-arrival-rate`, ép đủ số request/giây bất kể
  server trả lời kịp hay không. Trả lời "**trần thật ở đâu**".

Vì sao `browse` không tìm được trần: khi server chậm lại, mỗi VU tự gửi ít
request hơn (nó đang chờ response), tải tự co lại vừa đúng mức server chịu
được. Bạn chỉ thấy latency tăng dần, không bao giờ thấy điểm gãy.

---

## Quy trình chạy "kịch sàn"

### 1. Gỡ nút thắt cấu hình trước

Không làm bước này thì bài test chỉ đo được một connection pool đặt sai, không
phải phần cứng. Thay đổi đã có sẵn trong repo:

- `application.yml` — thêm `spring.datasource.hikari.*`, pool lấy từ
  `DB_POOL_SIZE` (mặc định vẫn 10 cho máy dev).
- `deploy/docker-compose.staging.yml` — `DB_POOL_SIZE=50`, Postgres
  `max_connections=200` + `shared_buffers=4GB`, JVM heap ghim `-Xmx6g`.

Triển khai theo `CONTRIBUTING.md` (PR vào `main` → self-hosted runner deploy).
Sau khi deploy xong, xác nhận đã ăn cấu hình mới:

```bash
cd /opt/pps-education/staging
sudo docker compose exec postgres psql -U pps_app -d pps_education \
  -c "SHOW max_connections; SHOW shared_buffers;"
sudo docker compose logs backend | grep -i hikari | head
```

### 2. Cài k6 trên server

```bash
sudo gpg -k && sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update && sudo apt install k6
```

Nâng giới hạn file descriptor cho phiên chạy k6 (mặc định 1024 là quá thấp):

```bash
ulimit -n 262144
```

### 3. Bật giám sát (terminal Cockpit thứ nhất)

```bash
tmux new -s monitor
cd ~/pps-education/security-lab   # hoặc nơi bạn để script
sudo ./monitor_server.sh staging 5 | tee ~/loadtest-$(date +%Y%m%d-%H%M).csv
# Ctrl-B rồi D để thoát ra
```

Cột cần theo dõi:

| Cột | Ý nghĩa khi bất thường |
|---|---|
| `pg_active` | Ghim đúng bằng `DB_POOL_SIZE` và đứng yên → **nghẽn pool**, không phải CPU |
| `pg_waiting` | Tăng vọt → tranh khoá / chờ I/O trong DB |
| `load1` | Vượt ~40 (số core) → CPU mới thực sự là trần |
| `mem_used_pct` | Tiến sát 100 → nguy cơ OOM-kill container |
| `http_502_1m` | Nginx không đẩy được vào backend → dấu hiệu chạm trần sớm nhất |
| `other_cpu_pct` | Production (khi nào chạy) đang bị ảnh hưởng bao nhiêu |

### 4. Chạy test tìm trần (terminal Cockpit thứ hai)

```bash
tmux new -s loadtest
export TARGET_URL=http://localhost:8081
export TEST_USERNAME=<tài_khoản_test>
export TEST_PASSWORD='<mật_khẩu>'

MODE=capacity RPS_PEAK=3000 k6 run loadtest_api_suite.js \
  --summary-export ~/k6-summary-$(date +%Y%m%d-%H%M).json
```

`RPS_PEAK=3000` là điểm khởi đầu để dò. Cách đọc:

- Nếu tới 3000 RPS mà `http_req_failed` vẫn ~0% và p95 còn thấp → **chưa chạm
  trần**, tăng `RPS_PEAK` lên 6000 rồi chạy lại.
- Nếu `dropped_iterations > 0` → **k6 không phát đủ tải**, giới hạn nằm ở máy
  chạy k6 chứ không phải server. Tăng `maxVUs` trong script hoặc `ulimit -n`.
- Nếu lỗi + p95 vọt lên ở một mốc RPS cụ thể → **đó là trần thật**. Đối chiếu
  mốc thời gian đó với CSV để biết nghẽn ở đâu (pool / CPU / RAM).

### 5. Dọn dữ liệu sau khi test

Phần ghi có 2 loại. `device-token` tự xoá ngay trong cùng iteration nên không
để lại gì. `lead` thì không có endpoint xoá (UC-34 chỉ cho chuyển đổi) nên phải
dọn bằng SQL — mọi bản ghi đều mang tiền tố `LOADTEST`:

```bash
sudo docker compose exec postgres psql -U pps_app -d pps_education
```

```sql
-- Xem trước SỐ LƯỢNG sẽ xoá, đừng xoá thẳng
SELECT count(*) FROM leads WHERE full_name LIKE 'LOADTEST %';
SELECT count(*) FROM device_tokens WHERE token LIKE 'LOADTEST-%';

-- leads_history.lead_id là NOT NULL REFERENCES leads(id) và KHÔNG có
-- ON DELETE CASCADE -> phải xoá bảng con TRƯỚC, nếu không sẽ lỗi FK.
BEGIN;
DELETE FROM leads_history
 WHERE lead_id IN (SELECT id FROM leads WHERE full_name LIKE 'LOADTEST %');
DELETE FROM leads WHERE full_name LIKE 'LOADTEST %';
DELETE FROM device_tokens WHERE token LIKE 'LOADTEST-%';
COMMIT;
```

> `device_tokens` **không có** cột `device_id` (chỉ `token`, `platform`,
> `user_id`) — trường `deviceId` trong `DeviceTokenRequest` không được lưu.
> Vì vậy lọc theo `token`, là chỗ script có gắn tiền tố `LOADTEST-`.
>
> `leads` dùng soft-delete (`deleted_at`) nhưng ở đây xoá cứng có chủ đích:
> lead của bài test là rác kỹ thuật, không phải dữ liệu nghiệp vụ cần lưu vết.
> Lưu ý unique index `idx_leads_phone` chỉ áp dụng `WHERE deleted_at IS NULL`,
> nên nếu chỉ soft-delete thì dải số điện thoại vẫn bị chiếm cho lần chạy sau.

Nếu muốn bỏ hẳn phần ghi: thêm `SKIP_WRITES=1`.

---

## Cloudflare có hấp thụ được tải không?

Câu trả lời ngắn cho `/api/**`: **gần như không, và không nên bật**.

- Mọi endpoint API đều có header `Authorization: Bearer`. Cloudflare mặc định
  không cache response của request có header đó → bạn sẽ thấy
  `CF-Cache-Status: DYNAMIC` ở hầu hết request.
- Quan trọng hơn: các endpoint này lọc dữ liệu theo `hasPermission(...)` và
  theo site của từng user. Cache một response rồi phục vụ cho user khác là
  **lộ dữ liệu chéo vai trò**, không chỉ là sai cấu hình. Đừng bật Cache Rule
  cho `/api/**`.
- Chỗ cache thật sự có ích: asset tĩnh của frontend, và
  `files-staging.ppsvietnam.edu.vn` (MinIO public GET).

Script đã có metric `cf_cache_hit` đọc từ header `CF-Cache-Status`, để bạn
**đo** thay vì tin lời khuyên. Muốn có phép đo đối chứng:

```bash
# lần 1 — qua cache
TARGET_URL=https://admin-staging.ppsvietnam.edu.vn k6 run loadtest_api_suite.js
# lần 2 — ép đi thẳng origin
BYPASS_CACHE=1 TARGET_URL=https://admin-staging.ppsvietnam.edu.vn k6 run loadtest_api_suite.js
```

Chênh lệch giữa hai lần mới là phần cache thực sự hấp thụ được.

---

## Checklist an toàn

- [ ] Đã báo trước cho người giữ staging, chọn khung giờ ít ảnh hưởng.
- [ ] Tài khoản test riêng đã seed, **không** dùng tài khoản người thật.
- [ ] Truyền credential qua biến môi trường — không hardcode vào file (repo public).
- [ ] Biết `max-failed-attempts=5`: sai mật khẩu 5 lần là khoá tài khoản 15
      phút (423) và mọi số liệu sau đó vô nghĩa. Script tự dừng nếu gặp 423.
- [ ] Đã kiểm tra production có đang chạy trên cùng server không
      (`sudo docker ps`). Nếu có: **hai stack chưa có resource limit**, tải vào
      staging sẽ ảnh hưởng production — đặt `cpus`/`mem_limit` trước.
- [ ] Server chưa có UPS, `/` chỉ 98GB — theo dõi `disk_root_pct` trong CSV,
      log của một bài test dài có thể phình nhanh.

## Điểm mù của Cockpit

Cockpit rất tốt cho CPU/RAM/disk/network mức hệ điều hành, trạng thái systemd,
và có sẵn terminal trong browser. Nhưng nó **không** thấy: số connection
Postgres đang active (nút thắt HikariCP), tách bạch tài nguyên giữa 2 stack,
heap/GC của JVM. Đó là lý do có `monitor_server.sh`.

`/actuator/metrics` và `/actuator/env` không được expose (NFR-SEC-03) nên không
đo được từ phía k6. Lưu ý thêm: `/actuator/health` trả `DOWN` dù backend khỏe
mạnh (do Mail Health Indicator, SMTP chưa cấu hình) — đừng dùng nó làm tín hiệu
sống/chết trong bài test.
