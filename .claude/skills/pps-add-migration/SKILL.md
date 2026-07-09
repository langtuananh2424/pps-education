---
name: pps-add-migration
description: Tạo 1 file Flyway migration mới cho backend PPS Education đúng quy ước dự án (không sửa migration cũ, đánh số tăng dần, đặt đúng thư mục). Chỉ gọi thủ công bằng /pps-add-migration, không tự động kích hoạt.
disable-model-invocation: true
allowed-tools: Bash(ls pps-education-backend/src/main/resources/db/migration/*) Read Write
---

Tạo migration Flyway mới cho: $ARGUMENTS

## Các bước bắt buộc

1. Liệt kê migration hiện có:
   `ls pps-education-backend/src/main/resources/db/migration/`
   Xác định số `V{n}` lớn nhất đang tồn tại.

2. **KHÔNG BAO GIỜ** sửa nội dung 1 file `V*.sql` đã tồn tại — nếu cần sửa
   lỗi ở bảng đã có, tạo migration mới để `ALTER`/`UPDATE`, không sửa file
   cũ (xem `CONTRIBUTING.md` mục "Quy ước migration Flyway").

3. Tạo file mới tại
   `pps-education-backend/src/main/resources/db/migration/V{n+1}__mo_ta_ngan.sql`
   (mô tả ngắn, snake_case, không dấu, không khoảng trắng).

4. Nội dung file phải:
   - Có comment đầu file nêu rõ UC/FR liên quan và lý do thay đổi.
   - Nếu tạo bảng mới: theo đúng quy ước đã dùng trong các migration trước
     — `id BIGSERIAL PRIMARY KEY`, `uuid UUID UNIQUE NOT NULL DEFAULT
     gen_random_uuid()` cho bảng cần định danh công khai, `created_at
     TIMESTAMPTZ NOT NULL DEFAULT now()` / `updated_at TIMESTAMPTZ NOT NULL
     DEFAULT now()` cho bảng cần audit.
   - Không `DROP TABLE`/`DROP COLUMN` trừ khi người dùng xác nhận rõ ràng
     là được phép mất dữ liệu.
   - Đối chiếu đúng tên bảng/cột đã thiết kế trong `docs/sdd-groups/` — nếu
     bảng/cột chưa có trong SDD, hỏi lại người dùng trước khi tự đặt tên.

5. Sau khi tạo file, nhắc người dùng: cần thêm/khớp Entity JPA tương ứng
   trong `domain/` nếu migration này phục vụ 1 tính năng đang code.
