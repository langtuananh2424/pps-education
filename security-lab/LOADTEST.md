# Load test hướng dẫn — chạy trên hệ thống CỦA BẠN

Mình không tự phóng traffic tới địa chỉ mạng thật từ phiên này (xem lý do
ở `README.md`). File này hướng dẫn để **bạn tự chạy** `loadtest_login_flow.js`
nhắm vào staging của mình, từ máy/mạng do bạn kiểm soát.

## Checklist trước khi chạy

- [ ] Đã báo trước cho team/người quản lý staging về thời điểm test.
- [ ] Chọn khung giờ ít ảnh hưởng người dùng thật (VD ngoài giờ làm việc).
- [ ] Có 1 tài khoản test riêng đã seed sẵn (KHÔNG dùng tài khoản người
      dùng thật) — tránh trigger khoá tài khoản (5 lần sai) làm sai lệch
      kết quả hoặc khoá nhầm ai đó.
- [ ] Có cách theo dõi phía server song song lúc test: log ứng dụng,
      `docker stats` (nếu deploy bằng `docker-compose.staging.yml`), hoặc
      dashboard APM/CloudWatch nếu có — vì `/actuator/metrics` không được
      public (NFR-SEC-03), bạn không đo được từ chính k6.
- [ ] Đã set `thresholds` trong script (đã có sẵn: dừng sớm nếu
      `http_req_failed > 10%`) để tránh kéo dài tình trạng quá tải ngoài
      ý muốn.

## Cài k6 và chạy

```bash
# cài k6 (1 lần) -- xem https://k6.io/docs/get-started/installation/
TARGET_URL=https://staging.your-domain.com \
TEST_USERNAME=loadtest_user \
TEST_PASSWORD='mat-khau-test-cua-ban' \
k6 run security-lab/loadtest_login_flow.js
```

## Cách đọc kết quả

k6 in ra summary cuối cùng gồm:

| Chỉ số | Ý nghĩa | Ngưỡng tham khảo |
|---|---|---|
| `http_req_duration p(95)` | 95% request nhanh hơn giá trị này | Càng thấp càng tốt; tăng đột biến ở VU nào là dấu hiệu chạm trần năng lực tại đó |
| `http_req_failed` | Tỉ lệ request lỗi (timeout, 5xx) | >5-10% nghĩa là server bắt đầu không kịp phục vụ |
| `iterations` / `iteration_duration` | Số vòng lặp (1 user login→me→refresh→logout) hoàn thành | So với kỳ vọng thực tế (VD giờ cao điểm có bao nhiêu người dùng đồng thời) |

**Điểm cần đối chiếu riêng cho hệ thống này:** vì HikariCP mặc định chỉ
10 connection/instance, nếu `p(95)` bắt đầu tăng mạnh quanh mốc VU=10 mà
CPU server vẫn thấp → nút thắt là **connection pool**, không phải CPU/RAM.
Cách xử lý (không nằm trong phạm vi lab này, chỉ ghi chú): tăng
`spring.datasource.hikari.maximum-pool-size` có cân nhắc với
`postgresql.max_connections`, hoặc scale thêm instance.

## Công cụ thay thế nếu không muốn cài k6

- `hey` (Go, 1 binary, đơn giản hơn): `hey -z 30s -c 10 https://.../actuator/health`
  — chỉ đo được endpoint không cần auth, không mô phỏng được luồng login đầy đủ.
- Apache Bench (`ab`): tương tự `hey`, cũ hơn nhưng có sẵn trên nhiều distro.

Cả hai đều đơn giản hơn `loadtest_login_flow.js` nhưng không mô phỏng được
luồng nghiệp vụ thật (login → dùng token → refresh) nên kết quả ít phản
ánh capacity thực tế bằng.
