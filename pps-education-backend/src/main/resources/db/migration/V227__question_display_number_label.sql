-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — nhãn số hiển thị TUỲ CHỌN, dùng khi
-- 1 câu "chiếm" nhiều ô đánh số liền nhau trong đề gốc (VD IELTS "Questions 14 and 15 — Choose TWO
-- letters A-E", 1 câu MULTIPLE_ANSWER nhưng đề in sẵn 2 số). Khác display_number_override (số nguyên,
-- dùng để TÍNH thứ tự tiếp theo) — cột này THUẦN HIỂN THỊ, không tham gia logic đếm/sắp xếp: NULL (mặc
-- định) = hiện số thường (display_number_override hoặc đếm tuần tự); có giá trị = hiện ĐÚNG chuỗi này
-- thay cho số (VD "14-15"), display_number_override vẫn giữ nguyên vai trò tính số kế tiếp như cũ.
ALTER TABLE questions ADD COLUMN display_number_label VARCHAR(20);
