-- UC-40/UC-21: 1 Lesson (exams) có thể có NHIỀU Bài (exercises) cùng skill_category (VD 3 Bài Ngữ pháp
-- 8 câu/bài) — trước đây UC-21 chỉ giao được TỪNG Bài lẻ, giáo viên phải chọn nhiều lần. Bổ sung ngoài
-- SDD gốc, đã xác nhận với người dùng 2026-08-24: hệ thống tự động gộp các Bài cùng (exam, skill_category)
-- thành 1 Exercise gộp THẬT (clone exercise_questions từ các Bài nguồn) — học sinh làm 1 lượt, nộp 1 lần,
-- tính đạt/không đạt trên tổng số câu gộp. Nếu 1 kỹ năng chỉ có 1 Bài thì không gộp, chấm như bình thường.
ALTER TABLE exercises ADD COLUMN auto_merged BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN exercises.auto_merged IS 'TRUE = Exercise này do hệ thống tự sinh, gộp câu hỏi từ nhiều Bài nguồn cùng skill_category trong 1 Lesson (V145). Không cho sửa tay câu hỏi trực tiếp trên Bài này — sửa ở từng Bài nguồn, hệ thống tự tính lại.';

-- Con trỏ "bản gộp ĐANG DÙNG" cho mỗi (exam, skill_category). Khi tập nguồn đổi SAU KHI đã có học sinh
-- làm bản gộp hiện tại, tạo Exercise gộp MỚI (v2) rồi UPDATE con trỏ này sang bản mới — bản gộp cũ (v1)
-- giữ nguyên không đổi gì, vẫn còn nguyên cho các exercise_assignments/exercise_attempts cũ tham chiếu.
CREATE TABLE exercise_skill_merges (
    id                  BIGSERIAL PRIMARY KEY,
    exam_id             BIGINT NOT NULL REFERENCES exams(id),
    skill_category      VARCHAR(20) NOT NULL,
    merged_exercise_id  BIGINT NOT NULL REFERENCES exercises(id),
    UNIQUE (exam_id, skill_category)
);
CREATE INDEX idx_exercise_skill_merges_exercise ON exercise_skill_merges(merged_exercise_id);

-- Ghi lại bản gộp hiện tại gồm những Bài nguồn nào — dùng để so sánh phát hiện "tập nguồn đã đổi chưa"
-- mỗi khi có Bài cùng (exam, skill_category) được publish/archive mới.
CREATE TABLE exercise_merge_sources (
    id                   BIGSERIAL PRIMARY KEY,
    merged_exercise_id   BIGINT NOT NULL REFERENCES exercises(id),
    source_exercise_id   BIGINT NOT NULL REFERENCES exercises(id),
    UNIQUE (merged_exercise_id, source_exercise_id)
);
CREATE INDEX idx_exercise_merge_sources_merged ON exercise_merge_sources(merged_exercise_id);

COMMENT ON TABLE exercise_skill_merges IS 'Con trỏ bản gộp Exercise đang dùng cho mỗi (exam, skill_category) — nguồn cho ExerciseService#listPublishedForClass thay thế N Bài lẻ bằng đúng 1 Exercise gộp khi N>=2 (V145).';
COMMENT ON TABLE exercise_merge_sources IS 'Thành phần Bài nguồn của 1 Exercise gộp — dùng để phát hiện tập nguồn đã đổi (thêm/bớt Bài) kể từ lần gộp gần nhất (V145).';
