-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — thêm LISTENING vào
-- Exercise.SkillCategory (V136) cho nhóm "Bài nghe" mới (audio nghe chọn đáp án/điền đáp án/xếp từ
-- trong hộp). KHÔNG đổi kiểu cột (vẫn VARCHAR(20), đủ chỗ chứa "LISTENING") — chỉ cập nhật lại COMMENT
-- cho khớp danh sách giá trị hợp lệ mới nhất, tránh tài liệu DB lạc hậu.
COMMENT ON COLUMN exercises.skill_category IS 'Nhóm kỹ năng của Bài: READING/WRITING/VOCAB_GRAMMAR/LISTENING, NULL = chưa phân loại (dữ liệu cũ). Dùng để lọc "chọn đề Reading/Writing" ở Nhận xét học viên (UC-21) — xem Exercise.SkillCategory.';
