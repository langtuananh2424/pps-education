-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-20 — "Số thứ tự hiển thị ghi đè": GV soạn
-- đề nhanh qua Excel cần số thứ tự hiển thị cho học sinh KHỚP ĐÚNG số đã in sẵn trong ảnh/sơ đồ sách gốc
-- (VD sơ đồ IELTS Listening đánh số sẵn 36-40), thay vì hệ thống luôn tự đếm tuần tự từ 1 trong mỗi Bài
-- (xem CreateAndAssignExerciseModal.tsx — nơi tính exercise_questions.display_order lúc gắn câu hỏi vào
-- Bài). TÙY CHỌN, NULL = giữ nguyên hành vi tự đếm cũ (đa số câu hỏi không cần).
--
-- Lưu trên questions (không phải exercise_questions) vì override được set NGAY LÚC IMPORT (trước khi
-- biết câu hỏi sẽ gắn vào Bài nào) — xem QuestionImportService#resolveDisplayNumberOverride. Giới hạn đã
-- biết: nếu 1 câu hỏi được TÁI SỬ DỤNG ở nhiều Bài khác nhau (qua exercise_questions), số ghi đè áp dụng
-- GIỐNG HỆT ở mọi Bài dùng lại câu này — chỉ nên dùng cho câu hỏi soạn riêng cho 1 Bài cụ thể.
ALTER TABLE questions
    ADD COLUMN display_number_override INTEGER NULL;

COMMENT ON COLUMN questions.display_number_override IS 'Số thứ tự hiển thị ghi đè khi gắn vào Bài (V217); NULL = tự đếm tuần tự như cũ';
