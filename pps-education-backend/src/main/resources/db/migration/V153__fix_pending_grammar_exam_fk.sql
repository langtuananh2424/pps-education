-- Fix bug thật: V150 (RENAME COLUMN pending_homework_next_exercise_id ->
-- pending_homework_next_grammar_exam_id) chỉ đổi TÊN cột để phản ánh đúng ý
-- nghĩa mới ("Id Lesson (exams)... trước là Id 1 Exercise" — xem comment gốc
-- ở V150), nhưng QUÊN cập nhật luôn ràng buộc khóa ngoại: cột này vẫn
-- REFERENCES exercises(id) (tạo từ V127, lúc cột còn mang nghĩa exerciseId),
-- trong khi code Java (StudentCommentService) từ V150 luôn ghi vào đây id
-- của 1 Exam thật. Hậu quả: INSERT/UPDATE student_comments với 1 examId hợp
-- lệ bất kỳ sẽ luôn vi phạm FK (vì examId hiếm khi trùng 1 exerciseId có
-- thật) -- chặn hẳn tính năng chọn "BTVN buổi sau" theo kênh Ngữ pháp/Nghe.
ALTER TABLE student_comments
    DROP CONSTRAINT student_comments_pending_homework_next_exercise_id_fkey;

ALTER TABLE student_comments
    ADD CONSTRAINT student_comments_pending_homework_next_grammar_exam_id_fkey
        FOREIGN KEY (pending_homework_next_grammar_exam_id) REFERENCES exams(id);
