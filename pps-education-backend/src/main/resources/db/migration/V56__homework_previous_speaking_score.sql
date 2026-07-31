-- Tách "BTVN Nghe-nói buổi trước" khỏi "BTVN Ngữ pháp buổi trước" —
-- đối xứng với BTVN buổi sau đã tách 2 kênh ở V55. Bổ sung ngoài SDD
-- gốc, đã xác nhận với người dùng 2026-07-28 (ticket FE).
ALTER TABLE student_comments
    ADD COLUMN homework_previous_speaking_score VARCHAR(10) NULL;
