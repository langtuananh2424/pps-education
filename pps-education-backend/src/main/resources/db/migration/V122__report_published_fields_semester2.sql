-- =====================================================================
-- V122: BO SUNG report_template_published_fields (V113) - CAC TRUONG DIEM
-- KY NANG CON THIEU CHO GIUA KY 2 / CUOI KY 2 (MID2/END2), TRANSCRIPT va
-- GRADE_REPORT - da xac nhan voi nguoi dung 2026-08-13. Nhan ban dung 8
-- truong (5 ky nang + OVERALL/LEVEL/COMMENT) da co san cho MID1/END1.
-- =====================================================================

INSERT INTO report_template_published_fields (template_type, field_key, field_label, description, field_type, display_order) VALUES
-- TRANSCRIPT
('TRANSCRIPT', 'LISTENING_MID2', 'Điểm Nghe - Giữa kỳ 2', 'Điểm kỹ năng Listening đợt Giữa kỳ 2', 'FIELD', 24),
('TRANSCRIPT', 'READING_MID2', 'Điểm Đọc - Giữa kỳ 2', 'Điểm kỹ năng Reading đợt Giữa kỳ 2', 'FIELD', 25),
('TRANSCRIPT', 'SPEAKING_MID2', 'Điểm Nói - Giữa kỳ 2', 'Điểm kỹ năng Speaking đợt Giữa kỳ 2', 'FIELD', 26),
('TRANSCRIPT', 'WRITING_MID2', 'Điểm Viết - Giữa kỳ 2', 'Điểm kỹ năng Writing đợt Giữa kỳ 2', 'FIELD', 27),
('TRANSCRIPT', 'GRAMMAR_MID2', 'Điểm Ngữ pháp - Giữa kỳ 2', 'Điểm kỹ năng Grammar đợt Giữa kỳ 2', 'FIELD', 28),
('TRANSCRIPT', 'OVERALL_MID2', 'Điểm Tổng kết - Giữa kỳ 2', 'Điểm Overall đợt Giữa kỳ 2', 'FIELD', 29),
('TRANSCRIPT', 'LEVEL_MID2', 'Xếp loại - Giữa kỳ 2', 'Trình độ / Level đợt Giữa kỳ 2', 'FIELD', 30),
('TRANSCRIPT', 'COMMENT_MID2', 'Nhận xét - Giữa kỳ 2', 'Lời nhận xét đợt Giữa kỳ 2', 'FIELD', 31),
('TRANSCRIPT', 'LISTENING_END2', 'Điểm Nghe - Cuối kỳ 2', 'Điểm kỹ năng Listening đợt Cuối kỳ 2', 'FIELD', 32),
('TRANSCRIPT', 'READING_END2', 'Điểm Đọc - Cuối kỳ 2', 'Điểm kỹ năng Reading đợt Cuối kỳ 2', 'FIELD', 33),
('TRANSCRIPT', 'SPEAKING_END2', 'Điểm Nói - Cuối kỳ 2', 'Điểm kỹ năng Speaking đợt Cuối kỳ 2', 'FIELD', 34),
('TRANSCRIPT', 'WRITING_END2', 'Điểm Viết - Cuối kỳ 2', 'Điểm kỹ năng Writing đợt Cuối kỳ 2', 'FIELD', 35),
('TRANSCRIPT', 'GRAMMAR_END2', 'Điểm Ngữ pháp - Cuối kỳ 2', 'Điểm kỹ năng Grammar đợt Cuối kỳ 2', 'FIELD', 36),
('TRANSCRIPT', 'OVERALL_END2', 'Điểm Tổng kết - Cuối kỳ 2', 'Điểm Overall đợt Cuối kỳ 2', 'FIELD', 37),
('TRANSCRIPT', 'LEVEL_END2', 'Xếp loại - Cuối kỳ 2', 'Trình độ / Level đợt Cuối kỳ 2', 'FIELD', 38),
('TRANSCRIPT', 'COMMENT_END2', 'Nhận xét - Cuối kỳ 2', 'Lời nhận xét đợt Cuối kỳ 2', 'FIELD', 39),

-- GRADE_REPORT
('GRADE_REPORT', 'LISTENING_MID2', 'Điểm Nghe - Giữa kỳ 2', 'Điểm kỹ năng Listening đợt Giữa kỳ 2', 'FIELD', 23),
('GRADE_REPORT', 'READING_MID2', 'Điểm Đọc - Giữa kỳ 2', 'Điểm kỹ năng Reading đợt Giữa kỳ 2', 'FIELD', 24),
('GRADE_REPORT', 'SPEAKING_MID2', 'Điểm Nói - Giữa kỳ 2', 'Điểm kỹ năng Speaking đợt Giữa kỳ 2', 'FIELD', 25),
('GRADE_REPORT', 'WRITING_MID2', 'Điểm Viết - Giữa kỳ 2', 'Điểm kỹ năng Writing đợt Giữa kỳ 2', 'FIELD', 26),
('GRADE_REPORT', 'GRAMMAR_MID2', 'Điểm Ngữ pháp - Giữa kỳ 2', 'Điểm kỹ năng Grammar đợt Giữa kỳ 2', 'FIELD', 27),
('GRADE_REPORT', 'OVERALL_MID2', 'Điểm Tổng kết - Giữa kỳ 2', 'Điểm Overall đợt Giữa kỳ 2', 'FIELD', 28),
('GRADE_REPORT', 'LEVEL_MID2', 'Xếp loại - Giữa kỳ 2', 'Trình độ / Level đợt Giữa kỳ 2', 'FIELD', 29),
('GRADE_REPORT', 'COMMENT_MID2', 'Nhận xét - Giữa kỳ 2', 'Lời nhận xét đợt Giữa kỳ 2', 'FIELD', 30),
('GRADE_REPORT', 'LISTENING_END2', 'Điểm Nghe - Cuối kỳ 2', 'Điểm kỹ năng Listening đợt Cuối kỳ 2', 'FIELD', 31),
('GRADE_REPORT', 'READING_END2', 'Điểm Đọc - Cuối kỳ 2', 'Điểm kỹ năng Reading đợt Cuối kỳ 2', 'FIELD', 32),
('GRADE_REPORT', 'SPEAKING_END2', 'Điểm Nói - Cuối kỳ 2', 'Điểm kỹ năng Speaking đợt Cuối kỳ 2', 'FIELD', 33),
('GRADE_REPORT', 'WRITING_END2', 'Điểm Viết - Cuối kỳ 2', 'Điểm kỹ năng Writing đợt Cuối kỳ 2', 'FIELD', 34),
('GRADE_REPORT', 'GRAMMAR_END2', 'Điểm Ngữ pháp - Cuối kỳ 2', 'Điểm kỹ năng Grammar đợt Cuối kỳ 2', 'FIELD', 35),
('GRADE_REPORT', 'OVERALL_END2', 'Điểm Tổng kết - Cuối kỳ 2', 'Điểm Overall đợt Cuối kỳ 2', 'FIELD', 36),
('GRADE_REPORT', 'LEVEL_END2', 'Xếp loại - Cuối kỳ 2', 'Trình độ / Level đợt Cuối kỳ 2', 'FIELD', 37),
('GRADE_REPORT', 'COMMENT_END2', 'Nhận xét - Cuối kỳ 2', 'Lời nhận xét đợt Cuối kỳ 2', 'FIELD', 38);
