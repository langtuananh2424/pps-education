-- V145 (bổ sung, đã xác nhận với người dùng 2026-08-24) — fix UX thật đã gặp qua test tay: học sinh
-- làm 1 Bài GỘP thấy câu hỏi đánh số liền 1 mạch (0,1,2,3...), không biết câu nào thuộc Bài nguồn nào
-- ("nguphap"/"nguphap2"). Ghi lại nguồn của TỪNG câu khi clone (ExerciseService#cloneQuestionsInto) để
-- FE nhóm hiển thị theo Bài gốc (tiêu đề "nguphap" rồi tới câu của nó, tiếp "nguphap2"...) mà KHÔNG đổi
-- cơ chế chấm điểm (vẫn 1 exercise_attempt, 1 total_score trên đúng Exercise gộp như cũ). NULL = câu
-- hỏi bình thường (không thuộc Bài gộp nào) — mọi exercise_questions cũ giữ nguyên NULL.
ALTER TABLE exercise_questions ADD COLUMN source_exercise_id BIGINT REFERENCES exercises(id);
COMMENT ON COLUMN exercise_questions.source_exercise_id IS 'Chỉ khác NULL khi dòng này được clone vào 1 Exercise gộp (exercises.auto_merged=TRUE) — trỏ về Bài nguồn thật để FE nhóm hiển thị theo Bài gốc.';
