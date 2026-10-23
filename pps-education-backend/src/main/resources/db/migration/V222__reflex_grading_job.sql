-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-22 — UC-23b (Video phản xạ): chấm AI qua HÀNG ĐỢI
-- trong PostgreSQL thay vì gọi AI ngay trong request nộp bài (35–90 giây/lượt, nghẽn pool kết nối, 504).
-- Hai làn tách hẳn nhau: WRITING (ưu tiên, học sinh đang chờ điểm) và SPEAKING (chấm nền). Mỗi làn có worker riêng.
-- Chỉ dùng khi bật cờ app.reflex-queue.enabled (mặc định tắt = luồng chấm đồng bộ cũ, không đụng tới bảng này).
CREATE TABLE reflex_grading_job (
    id BIGSERIAL PRIMARY KEY,
    reflex_question_progress_id BIGINT NOT NULL REFERENCES reflex_question_progress(id),
    lane VARCHAR(10) NOT NULL,
    -- Số lần nộp của bước tại thời điểm xếp hàng (writing_attempt_count / speaking_attempt_count) — để bỏ job cũ
    -- nếu bản ghi tiến trình đã sang lượt nộp khác.
    attempt_number INT NOT NULL,
    -- V199: bản ghi đã qua bộ lọc thu âm của FE hay chưa (chỉ bước nói; null = FE cũ), giữ lại để ghi vào lịch sử.
    recording_filter BOOLEAN,
    status VARCHAR(10) NOT NULL DEFAULT 'QUEUED',
    try_count INT NOT NULL DEFAULT 0,
    next_run_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(100),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reflex_grading_job_lane CHECK (lane IN ('WRITING', 'SPEAKING')),
    CONSTRAINT chk_reflex_grading_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'REJECTED', 'FAILED'))
);

-- Mỗi (bản ghi tiến trình, làn) chỉ có tối đa 1 job đang hoạt động; job đã đóng thì cho phép xếp job mới (nộp lại).
CREATE UNIQUE INDEX uq_reflex_grading_job_active
    ON reflex_grading_job(reflex_question_progress_id, lane)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX idx_reflex_grading_job_claim ON reflex_grading_job(lane, status, next_run_at);

-- NONE = luồng đồng bộ cũ / chưa nộp; PENDING = đang chờ hàng đợi; GRADED = AI đã chấm; FAILED = AI lỗi hết số lần thử
-- (đã hoàn lượt, chờ giáo viên chấm tay); REJECTED = bản ghi bị từ chối (im lặng/lạc đề), vẫn tính lượt.
ALTER TABLE reflex_question_progress
    ADD COLUMN writing_grading_status VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN speaking_grading_status VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD CONSTRAINT chk_reflex_progress_writing_grading_status
        CHECK (writing_grading_status IN ('NONE', 'PENDING', 'GRADED', 'FAILED', 'REJECTED')),
    ADD CONSTRAINT chk_reflex_progress_speaking_grading_status
        CHECK (speaking_grading_status IN ('NONE', 'PENDING', 'GRADED', 'FAILED', 'REJECTED'));

COMMENT ON TABLE reflex_grading_job IS 'UC-23b — hàng đợi chấm AI Clip phản xạ (2 làn WRITING/SPEAKING). Xem ReflexGradingQueueService.';
