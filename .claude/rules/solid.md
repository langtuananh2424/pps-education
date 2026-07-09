---
paths:
  - "pps-education-backend/src/main/java/**/*.java"
---

# Nguyên tắc SOLID áp dụng cho Spring Boot — có ví dụ cụ thể

Khi viết hoặc review code Java trong backend, kiểm tra đối chiếu 5 nguyên
tắc sau. Mỗi nguyên tắc có ví dụ SAI/ĐÚNG lấy từ đúng domain PPS Education
để không mơ hồ.

## S — Single Responsibility

1 class chỉ có 1 lý do để thay đổi. Trong dự án này, quy tắc cụ thể:

- **1 Service = 1 nhóm nghiệp vụ chặt** (thường ứng với vài UC liên quan
  trực tiếp), không phải 1 Service cho cả Phân hệ.
- SAI: `PermissionService` vừa xử lý đăng nhập (UC-01) vừa xử lý phân quyền
  (UC-02..05) — 2 lý do thay đổi khác nhau (đổi thuật toán hash mật khẩu
  không liên quan gì tới đổi cách tính effective_permissions).
- ĐÚNG: tách `AuthService` (UC-01) và `PermissionService`/`RoleService`
  (UC-02..05) dù cùng Phân hệ 2.
- Áp dụng tương tự cho Controller: 1 Controller = 1 nhóm resource REST liên
  quan (`AuthController` cho `/api/auth/**`, không nhét thêm endpoint quản
  lý quyền vào đây).

## O — Open/Closed

Mở rộng được mà không sửa code đã chạy ổn định. Áp dụng khi 1 UC có **nhiều
biến thể cố định** được liệt kê rõ trong SRS/SDD:

- Ví dụ kinh điển trong dự án: UC-09 (Chấm công) có 3 phương thức xác thực
  (GPS/vân tay/khuôn mặt — `system_settings.attendance.*_enabled`). Thêm
  phương thức thứ 4 sau này (VD QR code tại quầy) không được sửa lại
  `if/else` trong `AttendanceService` — phải là 1 implementation mới của
  interface, không đụng implementation cũ.
- Pattern khuyến nghị: `interface AttendanceMethod { boolean validate(...); }`,
  mỗi phương thức 1 class implement, Service inject `List<AttendanceMethod>`
  và chọn theo `system_settings` — thêm phương thức = thêm 1 class + 1
  dòng cấu hình, không sửa `AttendanceService`.
- Áp dụng tương tự: UC-24/27/41 (chấm tự động vs chấm thủ công theo loại
  câu hỏi), UC-30 (thanh toán QR vs thủ công).

## L — Liskov Substitution

Class con phải thay thế được class cha mà không phá hành vi đã cam kết.

- Interface Repository: không override 1 query method để trả `null` khi
  cha (Spring Data JPA) cam kết trả `Optional`/list rỗng.
- Khi có nhiều implementation của 1 interface Service (VD nhiều
  `NotificationSender`: Email/SMS/Push — xem module Notification dùng
  chung ở Backend Phase B), mọi implementation phải tuân thủ đúng
  precondition/postcondition khai báo ở interface — không được
  implementation nào throw thêm checked exception ngoài dự kiến hoặc yêu
  cầu thêm điều kiện đầu vào khắt khe hơn cha.

## I — Interface Segregation

Không ép 1 class phụ thuộc vào method nó không dùng.

- SAI: 1 interface `AcademicService` khổng lồ gồm cả method của UC-16
  (khung chương trình) lẫn UC-19/20 (điểm) lẫn UC-21/22 (nhận xét) — Service
  nào cũng phải implement/biết hết.
- ĐÚNG: tách theo nhóm UC như đã nói ở mục S — hệ quả tự nhiên của SRP tốt
  thường giải quyết luôn ISP.
- Với REST resource dùng chung nhiều nơi (VD `ApprovalFlowRepository` dùng
  chung cho UC-17/19-20/21-22 qua bảng `approval_flows`): tách method theo
  client cần, không dồn hết method có thể có vào 1 interface.

## D — Dependency Inversion

Module cấp cao phụ thuộc abstraction, không phụ thuộc chi tiết cài đặt.

- Service phụ thuộc `interface XxxRepository` (Spring Data JPA đã tự cho
  điều này qua interface) — không tự viết SQL thô kiểu
  `jdbcTemplate.query(...)` rải rác trong Service trừ khi thực sự cần
  (báo cáo phức tạp — khi đó bọc trong 1 Repository riêng, không đặt SQL
  thô ngay trong Service).
- Khi 1 hành vi có thể thay đổi theo môi trường/cấu hình (gửi thông báo
  Email vs mock trong test, xác thực GPS thật vs giả lập), Service phụ
  thuộc interface, implementation cụ thể được wire qua Spring
  `@Configuration`/profile — không `new` trực tiếp implementation trong
  Service.

## Lưu ý về mức độ áp dụng

Không áp dụng SOLID một cách giáo điều cho code đơn giản (VD 1 DTO record,
1 Repository interface 3 dòng không cần tách gì thêm). Áp dụng khi class
thực sự có dấu hiệu vi phạm (nhiều lý do thay đổi, nhiều if/else theo loại,
interface quá to) — ưu tiên code dễ đọc hơn là tách lớp máy móc.
