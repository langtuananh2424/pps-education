-- Chuyển department_id/is_management từ users sang employees — 2 cột này
-- chỉ có ý nghĩa với nhân sự, đúng nguyên tắc "thông tin đặc thù nằm ở
-- bảng mở rộng riêng" đã nêu trong docs/sdd-groups/02-nen-tang.md.

-- Bước 1: thêm cột mới ở employees (nullable/default trước để backfill an toàn).
ALTER TABLE employees ADD COLUMN department_id BIGINT NULL REFERENCES departments(id);
ALTER TABLE employees ADD COLUMN is_management BOOLEAN NOT NULL DEFAULT FALSE;

-- Bước 2: backfill từ dữ liệu users hiện có — chạy đúng cả khi employees rỗng.
UPDATE employees e
SET department_id = u.department_id,
    is_management = u.is_management
FROM users u
WHERE u.id = e.user_id;

-- Bước 3: bỏ cột cũ ở users — đã chuyển hết ý nghĩa sang employees.
ALTER TABLE users DROP COLUMN department_id;
ALTER TABLE users DROP COLUMN is_management;
