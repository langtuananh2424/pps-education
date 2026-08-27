-- UC-21/UC-40: thay cơ chế "gộp Bài" bằng clone câu hỏi (V145/V149) bằng "Lô giao BTVN theo kỹ năng"
-- (Homework Skill Batch) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24. Toàn bộ
-- V145/V149 chưa từng lên môi trường chung (chỉ chạy ở nhánh feature/local), an toàn xoá qua migration
-- MỚI theo đúng quy ước CONTRIBUTING.md (không sửa migration cũ).
--
-- Lý do đổi: cơ chế clone câu hỏi sang 1 Exercise ảo liên tục phát sinh bug (trùng dropdown, ẩn nhầm
-- khỏi danh sách GV, đánh số/tiêu đề không rõ khi hiển thị, tạo bản v2 âm thầm khi tập nguồn đổi sau khi
-- đã có học sinh làm). Thiết kế mới: mỗi Bài giữ nguyên là 1 Exercise thật độc lập; "Lô" chỉ là 1 lớp gom
-- MỎNG ở trên N bản ghi exercise_assignments thật (1 bản/Bài) — học sinh vẫn làm 1 lượt liên tục/nộp 1
-- lần (N ExerciseAttempt thật chạy song song, FE gộp hiển thị + gộp nộp), điểm/ngưỡng đạt cộng dồn từ N
-- attempt thay vì đọc từ 1 Exercise đã clone.
DROP TABLE exercise_merge_sources;
DROP TABLE exercise_skill_merges;
ALTER TABLE exercises DROP COLUMN auto_merged;
ALTER TABLE exercise_questions DROP COLUMN source_exercise_id;

-- 1 lô = 1 lần giáo viên chọn "(Lesson, Kỹ năng)" ở UC-21 để giao cho 1 lớp. skill_category NOT NULL —
-- đơn giản hoá so với thiết kế merge cũ: mỗi kênh UC-21 (Ngữ pháp/Reading/Writing/Nghe) giờ ứng đúng 1
-- skill_category cố định, không còn nhánh "Bài chưa phân loại (NULL) hiện trong kênh Ngữ pháp" — nhánh đó
-- chỉ tồn tại cho Bài tạo TRƯỚC V136, không có dữ liệu thật nào (nhánh chưa release).
CREATE TABLE homework_skill_batches (
    id                       BIGSERIAL PRIMARY KEY,
    exam_id                  BIGINT NOT NULL REFERENCES exams(id),
    skill_category           VARCHAR(20) NOT NULL,
    class_id                 BIGINT NOT NULL REFERENCES classes(id),
    assigned_by              BIGINT NOT NULL REFERENCES users(id),
    source_class_session_id  BIGINT REFERENCES class_sessions(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE homework_skill_batches IS 'Lô giao BTVN theo kỹ năng (V150) — gom N exercise_assignments (1 lô = 1 lần chọn (Lesson, Kỹ năng) ở UC-21) để FE Portal hiển thị/nộp gộp 1 lượt duy nhất. KHÔNG chứa nội dung câu hỏi — chỉ là lớp gom mỏng, mỗi exercise_assignments con vẫn đi nguyên luồng giao/chấm/nhắc hạn cũ.';

ALTER TABLE exercise_assignments ADD COLUMN homework_batch_id BIGINT REFERENCES homework_skill_batches(id);
COMMENT ON COLUMN exercise_assignments.homework_batch_id IS 'NULL = giao lẻ 1 Bài (hành vi cũ, không đổi). Có giá trị = 1 trong N bản giao thuộc cùng 1 lô BTVN theo kỹ năng (V150), cùng homework_batch_id nghĩa là cùng 1 lần giao ở UC-21.';

-- Đổi tên + đổi FK target của 8 cột trên student_comments (4 kênh Ngữ pháp/Reading/Writing/Nghe, thêm ở
-- V55/V123/V137/V146, đều "bổ sung ngoài SDD gốc" nên đổi được) — trỏ sang Lô thay vì 1 exercise_assignment
-- đơn; pending_* đổi từ "Id Exercise vừa chọn" sang "Id Lesson (exam) vừa chọn" vì lựa chọn giờ là
-- (Lesson, Kỹ năng) chứ không còn là 1 Exercise cụ thể — skill_category của mỗi kênh cố định theo cột.
ALTER TABLE student_comments RENAME COLUMN homework_next_exercise_assignment_id TO homework_next_grammar_batch_id;
ALTER TABLE student_comments RENAME COLUMN homework_next_reading_exercise_assignment_id TO homework_next_reading_batch_id;
ALTER TABLE student_comments RENAME COLUMN homework_next_writing_exercise_assignment_id TO homework_next_writing_batch_id;
ALTER TABLE student_comments RENAME COLUMN homework_next_listening_exercise_assignment_id TO homework_next_listening_batch_id;

ALTER TABLE student_comments DROP CONSTRAINT student_comments_homework_next_exercise_assignment_id_fkey;
ALTER TABLE student_comments DROP CONSTRAINT student_comments_homework_next_reading_exercise_assignment_fkey;
ALTER TABLE student_comments DROP CONSTRAINT student_comments_homework_next_writing_exercise_assignment_fkey;
ALTER TABLE student_comments DROP CONSTRAINT student_comments_homework_next_listening_exercise_assignme_fkey;
ALTER TABLE student_comments ADD CONSTRAINT student_comments_homework_next_grammar_batch_id_fkey FOREIGN KEY (homework_next_grammar_batch_id) REFERENCES homework_skill_batches(id);
ALTER TABLE student_comments ADD CONSTRAINT student_comments_homework_next_reading_batch_id_fkey FOREIGN KEY (homework_next_reading_batch_id) REFERENCES homework_skill_batches(id);
ALTER TABLE student_comments ADD CONSTRAINT student_comments_homework_next_writing_batch_id_fkey FOREIGN KEY (homework_next_writing_batch_id) REFERENCES homework_skill_batches(id);
ALTER TABLE student_comments ADD CONSTRAINT student_comments_homework_next_listening_batch_id_fkey FOREIGN KEY (homework_next_listening_batch_id) REFERENCES homework_skill_batches(id);

ALTER TABLE student_comments RENAME COLUMN pending_homework_next_exercise_id TO pending_homework_next_grammar_exam_id;
ALTER TABLE student_comments RENAME COLUMN pending_homework_next_reading_exercise_id TO pending_homework_next_reading_exam_id;
ALTER TABLE student_comments RENAME COLUMN pending_homework_next_writing_exercise_id TO pending_homework_next_writing_exam_id;
ALTER TABLE student_comments RENAME COLUMN pending_homework_next_listening_exercise_id TO pending_homework_next_listening_exam_id;

COMMENT ON COLUMN student_comments.homework_next_grammar_batch_id IS 'BTVN - Online - Ngữ pháp: Lô đã giao (homework_skill_batches, skill_category=VOCAB_GRAMMAR) cho buổi sau (V150, trước là 1 exercise_assignment đơn).';
COMMENT ON COLUMN student_comments.homework_next_reading_batch_id IS 'Mirror cột Ngữ pháp, skill_category=READING (V150).';
COMMENT ON COLUMN student_comments.homework_next_writing_batch_id IS 'Mirror cột Ngữ pháp, skill_category=WRITING (V150).';
COMMENT ON COLUMN student_comments.homework_next_listening_batch_id IS 'Mirror cột Ngữ pháp, skill_category=LISTENING (V150).';
COMMENT ON COLUMN student_comments.pending_homework_next_grammar_exam_id IS 'Id Lesson (exams) vừa chọn kỹ năng Ngữ pháp nhưng CHƯA Gửi nhận xét (V150, trước là Id 1 Exercise).';
COMMENT ON COLUMN student_comments.pending_homework_next_reading_exam_id IS 'Mirror cột Ngữ pháp cho Reading (V150).';
COMMENT ON COLUMN student_comments.pending_homework_next_writing_exam_id IS 'Mirror cột Ngữ pháp cho Writing (V150).';
COMMENT ON COLUMN student_comments.pending_homework_next_listening_exam_id IS 'Mirror cột Ngữ pháp cho Listening (V150).';
