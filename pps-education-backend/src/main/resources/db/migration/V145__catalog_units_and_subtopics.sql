-- UC-40 Kho đề: tái cấu trúc điều hướng theo đúng mục lục sách giáo trình thật —
-- Sách/Khối (curriculums.grade_level, đã có sẵn từ V140) -> Unit -> Sub Topic -> Lesson (exams, đã có) ->
-- Bài (exercises, đã có). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24. Mirror đúng pattern
-- curriculum_subjects (mô tả con trong 1 khung, không có uuid/code riêng, chỉ có display_order) — Unit/
-- SubTopic thuần là cấp điều hướng/phân loại nội dung, không có workflow duyệt như curriculums nên không
-- cần bảng history đi kèm.
CREATE TABLE units (
    id             BIGSERIAL PRIMARY KEY,
    curriculum_id  BIGINT NOT NULL REFERENCES curriculums(id),
    title          VARCHAR(300) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_units_curriculum ON units(curriculum_id);

CREATE TABLE sub_topics (
    id             BIGSERIAL PRIMARY KEY,
    unit_id        BIGINT NOT NULL REFERENCES units(id),
    title          VARCHAR(300) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_sub_topics_unit ON sub_topics(unit_id);

-- NULLABLE, không backfill Đề cũ (mirror quy ước skill_category V136: NULL = chưa phân loại, không đoán).
-- exams.curriculum_id GIỮ NGUYÊN (vẫn là điều kiện lọc/tìm kiếm) — sub_topic_id chỉ bổ sung thêm 1 cấp
-- điều hướng Unit/SubTopic phía trên Lesson, không thay thế field nào.
ALTER TABLE exams ADD COLUMN sub_topic_id BIGINT REFERENCES sub_topics(id);
CREATE INDEX idx_exams_sub_topic ON exams(sub_topic_id);

COMMENT ON TABLE units IS 'Unit trong sách giáo trình (VD "UNIT 1: MY NEW SCHOOL"), thuộc 1 khung chương trình (curriculum, đại diện 1 Khối). Xem docs/sdd-groups/09-lms-and-portal.md.';
COMMENT ON TABLE sub_topics IS 'Sub Topic trong 1 Unit (VD "SUB TOPIC 1: SCHOOL ACTIVITIES AND SUBJECTS"). 1 Lesson (exams.sub_topic_id) thuộc đúng 1 Sub Topic.';
COMMENT ON COLUMN exams.sub_topic_id IS 'Lesson thuộc Sub Topic nào trong mục lục sách — NULL = Đề cũ chưa phân loại vào cấu trúc mới (V144), không backfill đoán.';
