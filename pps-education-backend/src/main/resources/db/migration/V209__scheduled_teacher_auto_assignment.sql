-- =====================================================================
-- V209: Tự gán giáo viên vào lớp/điểm trường theo lịch dạy (bổ sung ngoài
-- SDD gốc, đã xác nhận với người dùng 2026-10-02).
--
-- Từ 2026-08-13 giáo viên của buổi học được chọn tay trên Lịch làm việc
-- (UC-48/56/57) và không còn gắn với class_teachers. Giáo viên MỚI chỉ được
-- xếp lịch (chưa gán tay ở tab Giáo viên — UC-18) thì không thấy lớp và
-- không có điểm trường. Từ V209:
--   - teacher_role có thêm giá trị 'SCHEDULED' ("Dạy theo lịch") — cột là
--     VARCHAR(20) không có CHECK constraint nên không cần ALTER. Hệ thống tự
--     tạo khi xếp/sửa buổi học cho giáo viên chưa có phân công đang hiệu lực
--     ở lớp, kèm site_teachers; tự kết thúc khi giáo viên không còn buổi học
--     nào ở lớp trong N ngày gần nhất / sắp tới (job hằng đêm).
--   - Cấu hình N = academic.scheduled_teacher_revoke_days (mặc định 30).
--   - Backfill: tạo phân công SCHEDULED cho các cặp (lớp, giáo viên) đang có
--     buổi học (chính/phụ/CM) không huỷ/không dời từ 30 ngày trước trở đi mà
--     chưa có phân công nào đang hiệu lực, cùng site_teachers còn thiếu.
-- =====================================================================

INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('academic.scheduled_teacher_revoke_days', '30', 'Số ngày giữ quyền vào lớp cho giáo viên "Dạy theo lịch" (tự gán khi xếp lịch) sau buổi học cuối cùng của họ ở lớp — quá hạn mà không còn buổi nào thì hệ thống tự kết thúc phân công', 'ACADEMIC');

CREATE TEMP TABLE v209_missing AS
SELECT cs.class_id,
       t.teacher_id,
       MIN(cs.session_date)                         AS first_date,
       (ARRAY_AGG(cs.created_by ORDER BY cs.id DESC))[1] AS assigned_by
FROM class_sessions cs
JOIN classes c ON c.id = cs.class_id AND c.deleted_at IS NULL
CROSS JOIN LATERAL (VALUES (cs.primary_teacher_id), (cs.assistant_teacher_id), (cs.cm_teacher_id)) AS t(teacher_id)
WHERE t.teacher_id IS NOT NULL
  AND cs.status NOT IN ('CANCELLED', 'RESCHEDULED')
  AND cs.session_date >= CURRENT_DATE - 30
  AND NOT EXISTS (SELECT 1 FROM class_teachers ct
                  WHERE ct.class_id = cs.class_id
                    AND ct.teacher_user_id = t.teacher_id
                    AND ct.assigned_to IS NULL)
GROUP BY cs.class_id, t.teacher_id;

WITH inserted AS (
    INSERT INTO class_teachers (class_id, teacher_user_id, teacher_role, assigned_from, assigned_by)
    SELECT m.class_id, m.teacher_id, 'SCHEDULED', LEAST(m.first_date, CURRENT_DATE), m.assigned_by
    FROM v209_missing m
    RETURNING id, teacher_user_id, assigned_by
)
INSERT INTO class_teachers_history (class_teacher_id, changed_by, action, details)
SELECT i.id, i.assigned_by, 'CREATED',
       jsonb_build_object('teacherUserId', i.teacher_user_id, 'teacherRole', 'SCHEDULED', 'reason', 'V209 backfill')
FROM inserted i;

INSERT INTO site_teachers (site_id, teacher_user_id, assigned_from, assigned_by, notes)
SELECT DISTINCT ON (c.site_id, m.teacher_id)
       c.site_id, m.teacher_id, CURRENT_DATE, m.assigned_by, 'V209: tự gán theo lịch dạy'
FROM v209_missing m
JOIN classes c ON c.id = m.class_id
WHERE NOT EXISTS (SELECT 1 FROM site_teachers st
                  WHERE st.site_id = c.site_id
                    AND st.teacher_user_id = m.teacher_id
                    AND st.assigned_to IS NULL)
ORDER BY c.site_id, m.teacher_id, m.class_id;

DROP TABLE v209_missing;
