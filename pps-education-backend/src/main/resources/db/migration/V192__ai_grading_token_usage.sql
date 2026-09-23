-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — đo chi phí token của luồng chấm AI
-- (UC-23b Video phản xạ và UC-40/41 chấm Writing). Log stdout (V192 NineRouterAiClient#logUsage) chỉ đủ
-- để cộng tổng theo ngày, KHÔNG truy vấn được "học sinh nào / bài nào / bước chấm nào tốn bao nhiêu" —
-- bảng này lưu 1 dòng cho MỖI lệnh gọi AI, phục vụ trang Quản trị hệ thống → Sử dụng token AI.
--
-- Vì sao MỌI cột ngữ cảnh đều NULL được: cùng 1 NineRouterAiClient phục vụ cả luồng Reflex (có học
-- sinh/bài/câu hỏi) lẫn luồng chấm Writing UC-40/41 và các lệnh gọi không gắn học sinh. Ghi được dòng
-- KHÔNG có ngữ cảnh vẫn tốt hơn bỏ sót chi phí — tổng trên trang phải khớp hoá đơn nhà cung cấp.
CREATE TABLE ai_grading_token_usage (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NULL REFERENCES students(id),
    review_video_assignment_id BIGINT NULL REFERENCES review_video_assignments(id),
    review_video_question_id BIGINT NULL REFERENCES review_video_questions(id),
    -- Bước chấm theo nghiệp vụ (KHÁC operation - tên kỹ thuật của lệnh gọi):
    -- WRITING = chấm bài viết, TRANSCRIPTION = phiên âm mù, SPEAKING = chấm bài nói,
    -- CORRECTED_ANSWER = sinh câu sửa mẫu, ESSAY = chấm Writing UC-40/41, OTHER = còn lại.
    step VARCHAR(30) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    requested_model VARCHAR(200),
    served_model VARCHAR(200),
    audio_attached BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_tokens INT NOT NULL DEFAULT 0,
    cached_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    -- Token "suy nghĩ" của model thinking: tính tiền như output nhưng KHÔNG nằm trong completion_tokens
    -- ở 1 số provider, nên tách cột riêng thay vì cộng gộp (xem NineRouterAiClient#extractReasoningTokens).
    reasoning_tokens INT NOT NULL DEFAULT 0,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    -- false = kết quả bị loại (9Router trả sai model, hoặc nội dung rỗng) nhưng token VẪN bị tính tiền.
    -- Tách cờ này để trang quản trị chỉ ra được phần chi phí bỏ đi, thay vì giấu nó trong tổng.
    accepted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Trang quản trị lọc theo khoảng ngày trước rồi mới gộp theo học sinh/bài, nên created_at là index chính.
CREATE INDEX idx_ai_token_usage_created_at ON ai_grading_token_usage (created_at);
CREATE INDEX idx_ai_token_usage_student ON ai_grading_token_usage (student_id, created_at);
CREATE INDEX idx_ai_token_usage_assignment ON ai_grading_token_usage (review_video_assignment_id, created_at);
