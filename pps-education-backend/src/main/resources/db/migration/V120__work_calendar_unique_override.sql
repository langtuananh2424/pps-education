-- =====================================================================
-- V120: Vá thiếu sót thiết kế gốc (V7) — work_calendar KHÔNG có ràng buộc
-- unique nào, cho phép insert nhiều override trùng calendar_date/scope.
-- AttendanceService.isWorkingDay() tra cứu bằng findBy...Optional (mong
-- đợi tối đa 1 bản ghi) — nếu trùng sẽ ném NonUniqueResultException lúc
-- chấm công thật (runtime crash khó debug). Bổ sung ngoài SDD gốc, đã
-- xác nhận với người dùng 2026-08-13 (đi kèm UC-70, V119).
--
-- 3 partial unique index riêng theo từng scope (giống pattern
-- idx_employee_shifts_active ở V7) vì shift_id/employee_id có thể NULL
-- tuỳ scope, không dùng chung 1 UNIQUE constraint thường được.
-- =====================================================================

CREATE UNIQUE INDEX idx_work_calendar_unique_all
    ON work_calendar (calendar_date) WHERE applies_to_scope = 'ALL';

CREATE UNIQUE INDEX idx_work_calendar_unique_shift
    ON work_calendar (calendar_date, shift_id) WHERE applies_to_scope = 'SHIFT';

CREATE UNIQUE INDEX idx_work_calendar_unique_employee
    ON work_calendar (calendar_date, employee_id) WHERE applies_to_scope = 'EMPLOYEE';
