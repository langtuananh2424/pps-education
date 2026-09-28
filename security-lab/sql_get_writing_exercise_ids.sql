-- Lấy EXERCISE_ID / ASSIGNMENT_ID / QUESTION_ID cho loadtest_writing_submissions.py
-- từ Bài Writing vừa giao ("Kho đề" -> Đề "Lesson 3" mã 7IELTS-ADV-C1-U1-S1-L3,
-- Bài "Ex.5 - Writing", giao cho lớp ND-2627-7E). Chạy trong pgAdmin, nhắm DB STAGING.
--
-- Sơ đồ bảng liên quan (xem domain Java tương ứng):
--   exams (Đề, cột "code" -- VD '7IELTS-ADV-C1-U1-S1-L3')
--     -> exercises (Bài trong Đề, cột exam_id, skill_category='WRITING' cho Ex.5)
--         -> exercise_questions (câu hỏi thuộc Bài, cột exercise_id/question_id)
--             -> questions (cột question_type='ESSAY' cho câu tự luận)
--         -> exercise_assignments (bản giao Bài cho 1 lớp, cột exercise_id/class_id)
--             -> classes (cột class_code -- VD 'ND-2627-7E')

-- CHỈNH 2 giá trị dưới đây cho đúng Đề/lớp thật của bạn trước khi chạy:
--   Mã Đề (exams.code)      : '7IELTS-ADV-C1-U1-S1-L3'
--   Mã lớp (classes.class_code): 'ND-2627-7E'

WITH target_exercise AS (
    SELECT
        e.id            AS exercise_id,
        e.code          AS exercise_code,
        e.title         AS exercise_title,
        e.skill_category,
        e.allow_retake,
        e.max_attempts,
        ex.code         AS exam_code,
        ex.title        AS exam_title
    FROM exercises e
    JOIN exams ex ON ex.id = e.exam_id
    WHERE ex.code = '7IELTS-ADV-C1-U1-S1-L3'
      AND e.skill_category = 'WRITING'
),
target_question AS (
    SELECT
        eq.exercise_id,
        q.id            AS question_id,
        q.question_type,
        LEFT(q.content, 200) AS content_preview,
        eq.points
    FROM exercise_questions eq
    JOIN questions q ON q.id = eq.question_id
    WHERE eq.exercise_id IN (SELECT exercise_id FROM target_exercise)
      AND q.question_type = 'ESSAY'
),
target_assignment AS (
    SELECT
        ea.id           AS assignment_id,
        ea.exercise_id,
        ea.status,
        ea.due_at,
        ea.late_submission_allowed,
        c.class_code
    FROM exercise_assignments ea
    JOIN classes c ON c.id = ea.class_id
    WHERE ea.exercise_id IN (SELECT exercise_id FROM target_exercise)
      AND c.class_code = 'ND-2627-7E'
)
SELECT
    te.exercise_id      AS "EXERCISE_ID",
    ta.assignment_id     AS "ASSIGNMENT_ID",
    tq.question_id       AS "QUESTION_ID",
    te.exercise_code,
    te.exercise_title,
    te.allow_retake,
    te.max_attempts,
    ta.status            AS assignment_status,
    ta.due_at,
    tq.content_preview   AS question_content_preview
FROM target_exercise te
LEFT JOIN target_question tq ON tq.exercise_id = te.exercise_id
LEFT JOIN target_assignment ta ON ta.exercise_id = te.exercise_id;

-- Đọc kết quả:
--   - Nếu ra ĐÚNG 1 dòng đủ cả 3 cột EXERCISE_ID/ASSIGNMENT_ID/QUESTION_ID -> dùng thẳng.
--   - Nếu ra 0 dòng: kiểm tra lại exams.code / classes.class_code có đúng chính tả
--     không (chạy 2 câu dò bên dưới), hoặc allow_retake=false / max_attempts < 3 (nếu
--     max_attempts có giá trị và nhỏ hơn số lượt bạn định bắn, sửa lại ở màn Soạn đề).
--   - Nếu ra NHIỀU dòng (2+ câu ESSAY trong Ex.5, hoặc Bài này giao nhiều lần cho
--     cùng lớp): script loadtest_writing_submissions.py chỉ hỗ trợ ĐÚNG 1 câu ESSAY --
--     chọn dòng có assignment mới nhất/đang ACTIVE, hoặc báo tôi để sửa script hỗ trợ
--     nhiều câu.

-- Dò lại nếu không chắc chính tả mã Đề/mã lớp (thay '%...%' bằng từ khoá bạn nhớ được):
-- SELECT id, code, title FROM exams WHERE code ILIKE '%L3%' OR title ILIKE '%Lesson 3%';
-- SELECT id, class_code, name FROM classes WHERE class_code ILIKE '%2627%' OR class_code ILIKE '%7E%';
