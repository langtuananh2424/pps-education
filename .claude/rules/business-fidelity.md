---
paths:
  - "pps-education-backend/src/main/java/**/service/**/*.java"
  - "pps-education-backend/src/main/java/**/domain/**/*.java"
  - "pps-education-backend/src/main/resources/db/migration/**/*.sql"
---

# Không sai lệch thông tin — `docs/` là nguồn chân lý duy nhất

Mục tiêu: code (entity, business logic, migration) không bao giờ được
"tự sáng tác" thêm hoặc khác đi so với những gì đã thống nhất trong
SRS/SDD/Đặc tả Use Case. Nếu code và docs lệch nhau, đó là bug — bất kể
code có "hợp lý" tới đâu.

## Quy trình bắt buộc trước khi viết business logic

1. Xác định mã UC đang implement. Đọc đúng file `docs/uc/phan-he-NN-*.md`
   chứa UC đó (dùng skill `pps-uc-lookup` hoặc `grep` trực tiếp).
2. Đối chiếu bảng/cột liên quan trong `docs/sdd-groups/0N-*.md` — dùng
   ĐÚNG tên bảng, tên cột, kiểu dữ liệu, ràng buộc đã thiết kế sẵn. Không
   tự đặt tên khác "cho dễ hiểu hơn", không tự đổi kiểu dữ liệu.
3. Map từng bước trong **Main Flow** thành code, từng nhánh trong
   **Alternate Flow** (A1, A2, ...) thành 1 nhánh xử lý/exception riêng —
   không gộp tắt, không bỏ sót nhánh nào.
4. Đối chiếu **Postcondition** làm điều kiện dừng của method — nếu
   Postcondition liệt kê 2 điều kiện (VD "trạng thái X được cập nhật" VÀ
   "người dùng Y nhận thông báo"), code phải làm cả 2, không chỉ 1.

## Khi phát hiện thiếu/mâu thuẫn thông tin — DỪNG LẠI, không tự đoán

Nếu 1 chi tiết cần để code (VD: giá trị mặc định, ngưỡng số, format chuỗi)
**không có trong SRS/SDD/UC**, đây là 3 lựa chọn theo thứ tự ưu tiên:

1. Tìm trong `docs/diagrams/activity/*.mmd` — nhiều luồng phức tạp có mô
   tả chi tiết hơn ở đây (VD ngưỡng `max_retry` của UC-15 điểm danh).
2. Nếu vẫn không có: hỏi lại người dùng, nêu rõ đang thiếu thông tin gì —
   KHÔNG tự bịa 1 giá trị "nghe hợp lý" rồi code tiếp.
3. Nếu người dùng xác nhận 1 quy tắc mới: nhắc cập nhật lại `docs/` (source
   of truth) — không chỉ sửa code mà để tài liệu lạc hậu.

## Cấm tự sáng tác

- Không tự thêm trạng thái (status/enum) ngoài danh sách đã liệt kê trong
  SDD. VD bảng `leads` chỉ có `NEW/CONTACTED/QUALIFIED/WON/LOST` — không tự
  thêm `IN_PROGRESS` vì "thấy thiếu".
- Không tự thêm cột/bảng vào migration nếu không có trong
  `docs/sdd-groups/`. Nếu thực sự cần (phát hiện thiếu sót thật sự trong
  thiết kế gốc), phải hỏi xác nhận trước, sau đó migration MỚI kèm comment
  giải thích rõ đây là bổ sung ngoài SDD gốc và lý do.
- Không tự đổi số bước duyệt, điều kiện rẽ nhánh so với Main
  Flow/Alternate Flow đã đặc tả — kể cả khi có vẻ "tối ưu hơn".

## Đối chiếu ngược khi review code người khác

Khi review 1 PR có business logic mới: mở đúng file UC trong `docs/uc/`,
đọc song song với code, tự hỏi "mỗi điều kiện rẽ nhánh trong PR này có
tương ứng với đúng 1 dòng trong Precondition/Main Flow/Alternate Flow
không, và không có nhánh nào bị bỏ sót". Đây là tiêu chí review, không chỉ
xét code có compile/chạy đúng hay không.

## Ghi chú bắt buộc trong code

Method Service chứa business logic của 1 UC phải có Javadoc dẫn chiếu mã
UC/FR, ví dụ:

```java
/**
 * UC-11: Duyệt đơn từ (FR-HRM-03).
 * Xem docs/uc/phan-he-04-nhan-su.md để biết đầy đủ Main Flow/Alternate Flow.
 */
public LeaveRequestDecisionResponse approve(...) { ... }
```
