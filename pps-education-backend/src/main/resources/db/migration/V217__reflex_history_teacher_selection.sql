-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-20 — giáo viên chọn LẦN VIẾT / NÓI TỐT NHẤT làm kết quả
-- chính thức (thay vì mặc định là lần nộp mới nhất) và chỉnh điểm ở lần đó. reflex_question_progress_history là
-- snapshot chỉ-thêm của AI (V191) nên điểm/nhận xét của giáo viên được lưu riêng, cạnh bản AI gốc trên CÙNG dòng:
-- phục vụ làm nhãn (AI gốc vs giáo viên) khi xuất dữ liệu để fine-tune mô hình.
--   teacher_selected = TRUE: giáo viên đã chọn lần này làm kết quả chính thức của (tiến trình, bước); tối đa 1 lần/bước.
--     Học sinh nộp lại bước đó thì lần mới thay thế và cờ này bị xoá (điểm giáo viên cũ vẫn giữ làm nhãn).
--   teacher_score/teacher_feedback: điểm + nhận xét giáo viên chốt cho lần này (NULL = giáo viên chưa đụng tới).
ALTER TABLE reflex_question_progress_history
    ADD COLUMN teacher_selected BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN teacher_score    NUMERIC(5, 2) NULL,
    ADD COLUMN teacher_feedback TEXT          NULL,
    ADD COLUMN teacher_by       BIGINT        NULL REFERENCES users (id),
    ADD COLUMN teacher_at       TIMESTAMPTZ   NULL;

CREATE UNIQUE INDEX idx_reflex_history_teacher_selected
    ON reflex_question_progress_history (reflex_question_progress_id, attempt_type)
    WHERE teacher_selected;

COMMENT ON COLUMN reflex_question_progress_history.teacher_selected IS 'Giáo viên chọn lần này làm kết quả chính thức của bước (V217); tối đa 1 lần/bước';
COMMENT ON COLUMN reflex_question_progress_history.teacher_score IS 'Điểm giáo viên chốt cho lần này (V217); NULL = chưa chỉnh';
