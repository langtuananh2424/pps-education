# Security Lab — An ninh mạng & mô phỏng tấn công (học tập)

Thư mục này KHÔNG phải code sản phẩm — là bài lab học tập, mô phỏng tấn
công/vá lỗi trong môi trường cô lập (chỉ chạy trên `127.0.0.1` trong
sandbox). Không kết nối ra mạng ngoài, không đụng tới staging/production.

## 1. Kiến thức nền — mạng máy tính & an ninh mạng

### Mô hình mạng cơ bản
- **TCP/IP 4 lớp** (thực tế dùng nhiều hơn OSI 7 lớp lý thuyết):
  Application (HTTP, DNS, TLS) → Transport (TCP/UDP, port) → Internet (IP,
  routing) → Link (Ethernet/Wi-Fi).
- Request `POST /api/auth/login` của PPS Education đi qua: TLS (mã hoá) →
  TCP (bắt tay 3 bước, đảm bảo tin cậy) → IP (định tuyến) → tới Nginx
  (`deploy/nginx/`) → Spring Boot app.

### Các nhóm tấn công phổ biến
| Nhóm | Ví dụ | Cơ chế |
|---|---|---|
| **Injection** | SQL Injection, Command Injection | Chèn code vào input, để hệ thống hiểu nhầm là lệnh thay vì dữ liệu |
| **Broken Authentication** | Brute-force, Credential Stuffing | Dò/đoán mật khẩu hàng loạt vì không có giới hạn số lần thử |
| **Information Disclosure** | User Enumeration | Lỗi trả về khác nhau giữa "sai user" và "sai password" → lộ danh sách tài khoản tồn tại |
| **Session/Token** | JWT tampering, Token replay, Session fixation | Giả mạo hoặc tái sử dụng token đã bị thu hồi |
| **MITM** | ARP spoofing, SSL stripping | Chen vào giữa 2 bên giao tiếp khi không có TLS/xác thực chứng chỉ |
| **DoS/DDoS** | Flood request, Slowloris | Làm cạn tài nguyên (CPU/RAM/băng thông/connection pool) khiến server không phục vụ được người dùng thật |
| **Social Engineering** | Phishing | Lừa người dùng tự tiết lộ thông tin, không khai thác lỗ hổng kỹ thuật |

### Nguyên tắc phòng thủ (Defense in Depth)
1. **Least privilege** — mỗi thành phần chỉ có quyền tối thiểu cần thiết.
2. **Validate & parameterize input** — không bao giờ tin dữ liệu từ client,
   không nối chuỗi để build query/lệnh.
3. **Fail generically** — thông báo lỗi không tiết lộ chi tiết nội bộ
   (tồn tại tài khoản hay không, stack trace...).
4. **Rate limit / lockout** — giới hạn số lần thử trong khoảng thời gian.
5. **Defense at multiple layers** — không dựa vào 1 lớp bảo vệ duy nhất
   (WAF + validate ở code + rate limit + giám sát log).
6. **Log & alert** — ghi nhận hành vi bất thường để phát hiện sớm, không
   chỉ để ngăn chặn.

## 2. Phân tích bảo mật thật trên `AuthService.java` (UC-01)

Đọc `pps-education-backend/src/main/java/vn/com/pps/education/service/AuthService.java`
— các cơ chế phòng thủ ĐÃ CÓ SẴN trong code thật:

| Cơ chế | Dòng code | Chống lại |
|---|---|---|
| Spring Data JPA — `findByUsername`/`findByEmail` sinh query tham số hoá | `AuthService.java:115-116` | SQL Injection |
| `passwordEncoder.matches(...)` (BCrypt) — không lưu/so sánh plaintext | `AuthService.java:130` | Lộ mật khẩu khi rò rỉ DB, timing attack thô sơ |
| Thông báo lỗi **giống hệt nhau** dù sai username hay sai password ("Sai tài khoản hoặc mật khẩu") | `AuthService.java:122-123, 134-135` | User enumeration |
| Đếm `failed_login_count`, khoá tài khoản `lockedUntil` sau `max-failed-attempts` (5 lần, cấu hình ở `application.yml:112`) trong `lockDurationMinutes` (15 phút) | `AuthService.java:290-299` | Brute-force |
| Refresh token: hash SHA-256 lưu DB (không lưu raw token), rotate mỗi lần dùng, phát hiện reuse token đã revoke → thu hồi toàn bộ session | `AuthService.java:184-213` | Token theft / replay |
| `@Transactional(noRollbackFor = ...)` để đảm bảo audit log (`login_attempts`) và trạng thái khoá được ghi lại **trước khi** throw exception | `AuthService.java:111-112` | Mất dấu vết tấn công do transaction rollback |

Đây chính là lý do lab bên dưới dựng bản "hardened" mô phỏng ĐÚNG các cơ
chế này (không phải bịa thêm) để đối chiếu với bản "naive" thiếu chúng.

## 3. Lab thực hành — đã chạy trong sandbox này

Do sandbox không có Docker/Postgres nên không dựng được Spring Boot thật;
2 server Python nhỏ dưới đây tái hiện đúng hành vi bảo mật của
`AuthService.java` để minh hoạ, không phải chạy code Java thật.

- `vulnerable_server.py` (port 8081) — cố tình viết sai: nối chuỗi SQL,
  so sánh plaintext, không khoá tài khoản, lộ user enumeration.
- `hardened_server.py` (port 8082) — mô phỏng đúng cơ chế bảng trên.
- `attack_sqli.py` — gửi payload SQL Injection (`admin' -- `, `' OR '1'='1`).
- `attack_bruteforce.py` — dò mật khẩu theo wordlist.

### Kết quả đã chạy

**SQL Injection:**
- Server naive: `admin' -- ` và `' OR '1'='1' -- ` → **bypass đăng nhập
  thành công** (HTTP 200, không cần biết mật khẩu thật).
- Server hardened: cả 3 payload đều bị chặn (HTTP 401, thông báo chung
  chung) — vì query dùng tham số hoá (`WHERE username = ?`), chuỗi payload
  chỉ được hiểu là *dữ liệu*, không phải *cú pháp SQL*.

**Brute-force (wordlist 8 mật khẩu, mật khẩu thật là `SuperSecret123`):**
- Server naive: dò được mật khẩu đúng ở lần thử thứ 6, không có gì ngăn
  cản (có thể tiếp tục dò cho tài khoản khác).
- Server hardened: sau 5 lần sai, tài khoản bị khoá — **lần thử thứ 6
  chính là mật khẩu đúng nhưng vẫn bị chặn (HTTP 423)** vì logic kiểm tra
  khoá tài khoản chạy TRƯỚC khi so mật khẩu (giống hệt thứ tự
  `ensureAccountUsable()` rồi mới `passwordEncoder.matches()` trong
  `AuthService.login()`).

### Tự chạy lại

```bash
cd security-lab
python3 vulnerable_server.py &   # port 8081
python3 hardened_server.py &     # port 8082
python3 attack_sqli.py
python3 attack_bruteforce.py
kill %1 %2   # dừng server sau khi xong
```

## 4. Về việc test staging/production thật

Lab này KHÔNG bao gồm tấn công vào staging/production thật — xem
`loadtest_template.js` (k6) để tự chạy load test hợp lệ, có giới hạn,
nhắm vào staging của chính bạn, từ máy/mạng do bạn kiểm soát. Không dùng
để tấn công DoS hay khai thác lỗ hổng trên hệ thống đang phục vụ người
dùng thật.
