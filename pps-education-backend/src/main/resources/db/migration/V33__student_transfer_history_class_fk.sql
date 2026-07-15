-- Bổ sung FK from_class_id/to_class_id -> classes(id) cho student_transfer_history.
-- V11__student_core.sql (dòng 71-74) đã để lại comment: bổ sung FK bằng migration
-- MỚI khi Phân hệ 6 (Học thuật) triển khai, không sửa lại file migration cũ.
-- Phân hệ 6 đã triển khai từ V12__academic_curriculum_class_core.sql (bảng classes).
ALTER TABLE student_transfer_history
    ADD CONSTRAINT fk_transfer_history_from_class FOREIGN KEY (from_class_id) REFERENCES classes(id),
    ADD CONSTRAINT fk_transfer_history_to_class FOREIGN KEY (to_class_id) REFERENCES classes(id);
