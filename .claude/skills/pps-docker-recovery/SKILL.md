---
name: pps-docker-recovery
description: Chẩn đoán và khôi phục khi Docker Desktop/Postgres container chết giữa phiên làm việc trên máy dev Windows của dự án PPS Education (docker ps/docker exec lỗi liên tục, app Spring Boot mất kết nối DB). Dùng khi cần verify runtime thật (docker compose up + mvn spring-boot:run) mà gặp lỗi kết nối Postgres hoặc lệnh docker treo/lỗi bất thường.
---

## Triệu chứng

- `docker ps`/`docker exec` liên tục lỗi hoặc treo — đôi khi bề ngoài
  giống lỗi tool/classifier ("temporarily unavailable") nhưng thực ra là
  do tiến trình `Docker Desktop.exe` đã không còn chạy (đã quan sát thực
  tế: máy sleep/resume có thể làm Docker Desktop's WSL2 backend treo).
- App Spring Boot (`mvn spring-boot:run`) log lỗi
  `HikariPool-1 - Connection is not available` /
  `java.net.ConnectException: Connection refused: getsockopt` /
  `Connection to localhost:15432 refused` lặp lại mỗi ~90s (scheduler
  NotificationDispatchService retry).

## Chẩn đoán nhanh

```bash
tasklist | grep -i "Docker Desktop"   # rỗng = Docker Desktop.exe đã chết
docker ps -a                          # lỗi "failed to connect to the docker API ... dockerDesktopLinuxEngine" = daemon chết
```

Lệnh local thuần (echo, đọc file, tail log) vẫn chạy bình thường trong
lúc này — chỉ lệnh chạm Docker/network mới lỗi. Đừng nhầm đây là outage
của tool Bash/PowerShell và ngồi retry vô ích.

## Quy trình khôi phục (đã verify chạy được nhiều lần)

1. Khởi động lại Docker Desktop, đợi daemon sẵn sàng:
   ```bash
   powershell -Command "Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'"
   until docker ps >/dev/null 2>&1; do sleep 3; done
   ```

2. Tạo lại container Postgres (an toàn — named volume `pps_pg_data` trong
   `docker-compose.yml` giữ nguyên dữ liệu kể cả khi container object bị
   tạo mới hoàn toàn, đã verify `flyway_schema_history`/`users` còn
   nguyên sau khi recreate):
   ```bash
   cd d:/pps-education-backend && docker compose up -d postgres
   until docker exec pps-education-db pg_isready -U pps_app >/dev/null 2>&1; do sleep 2; done
   ```

3. **Bắt buộc khởi động lại app Spring Boot** — app cũ (nếu tiến trình
   `java.exe` vẫn còn sống) sẽ KHÔNG tự hồi phục dù DB đã sống lại, vì
   Tomcat đã ngừng lắng nghe port 8080 từ trước đó:
   ```bash
   powershell -Command "Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force"
   cd d:/pps-education-backend/pps-education-backend
   export DB_URL="jdbc:postgresql://localhost:15432/pps_education"
   nohup mvn -o spring-boot:run > app-run.log 2>&1 &
   disown
   until grep -q "Started PpsEducationApplication\|BUILD FAILURE\|APPLICATION FAILED TO START" app-run.log 2>/dev/null; do sleep 3; done
   grep -m1 -E "Started PpsEducationApplication|BUILD FAILURE|APPLICATION FAILED TO START" app-run.log
   ```

4. Nếu build báo lỗi lạ không liên quan tới code vừa sửa (VD
   `ClassFormatError: Extra bytes at the end of class file` trên 1 class
   không hề đụng tới) — nghi ngờ `target/classes` bị corrupt do build bị
   ngắt giữa chừng lúc Docker/máy gặp sự cố trước đó. Fix: `mvn -o clean
   compile` (không chỉ `mvn -o compile` — cache incremental đôi khi báo
   "Nothing to compile" sai dù code đã đổi).

## Sau khi khôi phục

Dữ liệu fixture/test cũ tạo qua smoke test SQL (VD site/user/class test)
vẫn còn nguyên nhờ named volume — không cần tạo lại từ đầu, chỉ cần
login lại lấy JWT mới (access token có thời hạn ngắn, hết hạn giữa 2 lần
verify runtime nếu cách nhau lâu → lỗi 403 dễ nhầm là lỗi authorization
thật, thử login lại trước khi kết luận có bug).
