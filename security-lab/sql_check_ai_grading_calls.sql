-- Kiểm tra AI chấm Writing có THỰC SỰ gọi ra 9Router hay fail sớm trước khi
-- kịp gọi. WritingAiGradingService.grade() có 2 đường fail SỚM (KHÔNG gọi AI,
-- không sinh usage -> KHÔNG có dòng nào trong ai_grading_token_usage):
--   1. rubric == null (WritingAiGradingService.java:169-171)
--   2. WritingV3Grade.forGradeTrack(...) == null (dòng 196-200)
-- Và 1 đường fail SAU KHI đã gọi AI (CÓ dòng, accepted=false hoặc step=OTHER
-- -- xem AiGradingTokenUsageRecorder.recordRejected):
--   3. 9Router trả rỗng/lỗi, hoặc parse kết quả thất bại, hoặc bị cắt dở dang.
--
-- Nếu câu dưới ra 0 dòng cho khung giờ chạy load test (~13:45-13:47 giờ VN =
-- 06:45-06:47 UTC hôm nay) -> XÁC NHẬN rơi vào nhánh 1 hoặc 2 (rubric/grade-
-- track không tra được), KHÔNG PHẢI lỗi gọi AI thật.
-- Nếu CÓ dòng -> đổi hướng điều tra sang nhánh 3 (xem log backend đoạn
-- "WritingAiGradingService: 9Router chấm (v3) thất bại" / "kết quả dở dang"
-- / "parse kết quả chấm (v3) thất bại").

SELECT id, step, operation, requested_model, served_model, accepted,
       prompt_tokens, completion_tokens, elapsed_ms, created_at
FROM ai_grading_token_usage
WHERE created_at BETWEEN '2026-09-23 06:44:00+00' AND '2026-09-23 06:48:00+00'
ORDER BY created_at;

-- Đối chiếu thêm: curriculum (gradeLevel/track) của chính Đề "Lesson 3" --
-- rubricLoader.load() và WritingV3Grade.forGradeTrack() đều tra theo 2 giá
-- trị này. Nếu gradeLevel/track của Đề này KHÔNG khớp file rubric nào có
-- sẵn (xem resources rubric.md theo khối/track), đó chính là nguyên nhân.
SELECT
    ex.id AS exam_id,
    ex.code AS exam_code,
    c.id AS curriculum_id,
    c.grade_level,
    c.track
FROM exams ex
JOIN curriculums c ON c.id = ex.curriculum_id
WHERE ex.code = '7IELTS-ADV-C1-U1-S1-L3';
