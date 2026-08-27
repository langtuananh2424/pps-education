-- Kho Video Ôn tập: mirror đúng V144 (exams.sub_topic_id) — thêm cấp điều hướng Sách/Unit/SubTopic
-- (đã có sẵn từ V144/V148, dùng chung với Kho đề) phía trên "Bộ" (review_video_sets), song song với Đề
-- (exams -> "Lesson"). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26.
-- NULLABLE, không backfill (mirror quy ước exams.sub_topic_id V144: NULL = Bộ cũ chưa phân loại vào cấu
-- trúc mới, không đoán). curriculum_id GIỮ NGUYÊN là điều kiện lọc/tìm kiếm hiện có, không bị thay thế.
ALTER TABLE review_video_sets ADD COLUMN sub_topic_id BIGINT REFERENCES sub_topics(id);
CREATE INDEX idx_review_video_sets_sub_topic ON review_video_sets(sub_topic_id);

COMMENT ON COLUMN review_video_sets.sub_topic_id IS 'Bộ video thuộc Sub Topic nào trong mục lục sách — NULL = Bộ cũ chưa phân loại vào cấu trúc Sách/Unit/SubTopic (V153), không backfill đoán.';
