-- UC-40 Soạn & Giao đề: phân loại "Bài" (Exercise) theo nhóm kỹ năng Reading/Writing/Từ vựng & Ngữ pháp
-- (VOCAB_GRAMMAR) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-21. Field MỚI, độc lập với
-- exercise_type (SELF_PRACTICE/ASSIGNED/MOCK_TEST/SKILL_PRACTICE — cơ chế giao bài, khác nghĩa) và
-- exams.exam_type (REVIEW/HOMEWORK — mục đích sử dụng, khác nghĩa). Giáo viên tự chọn tường minh lúc
-- tạo Bài, không suy luận tự động từ loại câu hỏi bên trong. NULLABLE — Bài cũ trước migration này coi
-- là "chưa phân loại", không backfill đoán giá trị.
ALTER TABLE exercises ADD COLUMN skill_category VARCHAR(20);

COMMENT ON COLUMN exercises.skill_category IS 'Nhóm kỹ năng của Bài: READING/WRITING/VOCAB_GRAMMAR, NULL = chưa phân loại (dữ liệu cũ). Dùng để lọc "chọn đề Reading/Writing" ở Nhận xét học viên (UC-21) — xem Exercise.SkillCategory.';
