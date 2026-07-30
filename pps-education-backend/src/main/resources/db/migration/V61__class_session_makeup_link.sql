-- Liên kết buổi hủy (CANCELLED) và buổi bù (MAKEUP) — bổ sung ngoài SDD
-- gốc, đã xác nhận với người dùng 2026-07-29. FK 1 chiều đặt trên buổi
-- MAKEUP, trỏ về buổi CANCELLED nó bù cho. UNIQUE trên cột này (Postgres
-- cho phép nhiều NULL) đảm bảo 1 buổi hủy chỉ có ĐÚNG 1 buổi bù.
ALTER TABLE class_sessions ADD COLUMN makeup_for_session_id BIGINT
    REFERENCES class_sessions(id);
ALTER TABLE class_sessions ADD CONSTRAINT uq_class_sessions_makeup_for_session_id
    UNIQUE (makeup_for_session_id);
