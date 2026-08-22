-- =====================================================================
-- V128: Dao nguoc quyet dinh 2026-08-13 (V121) — giao vien phu trach 1
-- buoi hoc (class_sessions) tro lai CHON TAY tung buoi, khong con tu dong
-- suy ra tu class_teachers PRIMARY cua lop. Bo sung "GV phu" + "CM" gan
-- rieng theo TUNG BUOI (khac class_teachers la cap LOP) — xac nhan voi
-- nguoi dung 2026-08-19. Xem docs/uc/phan-he-06-hoc-thuat.md (UC-48/56/57).
-- =====================================================================

ALTER TABLE class_sessions
    ADD COLUMN assistant_teacher_id BIGINT NULL REFERENCES users(id),
    ADD COLUMN cm_teacher_id BIGINT NULL REFERENCES users(id);
