-- Bổ sung ngoài SDD gốc, theo yêu cầu người dùng 2026-10-22 — giáo viên chấm lại (V216/V217) được sửa điểm TỪNG band
-- (tiêu chí) của bước Viết/Nói, không chỉ điểm tổng.
--   reflex_question_progress_history.teacher_criteria_scores: % từng band giáo viên chốt cho lần này, cạnh bản AI gốc
--     (criteria_scores) để làm nhãn khi xuất dữ liệu; NULL = giáo viên chưa sửa band (chỉ sửa điểm tổng hoặc chưa đụng tới).
--   reflex_question_progress.writing_criteria_scores: % từng band của bước Viết (luồng v2) — trước đây chỉ bước Nói có
--     speaking_criteria_scores. Phản chiếu kết quả chính thức như các cột writing_* khác (giáo viên sửa thì ghi bản của GV).
ALTER TABLE reflex_question_progress_history
    ADD COLUMN teacher_criteria_scores JSONB NULL;

ALTER TABLE reflex_question_progress
    ADD COLUMN writing_criteria_scores JSONB NULL;

COMMENT ON COLUMN reflex_question_progress_history.teacher_criteria_scores IS 'Điểm % từng band giáo viên chốt cho lần này (V219); NULL = chưa sửa band';
COMMENT ON COLUMN reflex_question_progress.writing_criteria_scores IS 'Điểm % từng band bước Viết (V219); NULL = luồng cũ / chưa chấm';
