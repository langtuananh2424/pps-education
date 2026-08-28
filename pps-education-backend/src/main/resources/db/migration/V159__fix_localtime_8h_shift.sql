-- =====================================================================
-- V159: DATA-FIX +8h cho cac cot LocalTime (bug hibernate.jdbc.time_zone)
-- Bo sung ngoai SDD goc. Ban va HA TANG - khong doi schema, chi sua DU
-- LIEU CU bi lech -8h.
--
-- NGUYEN NHAN: truoc 2026-08-20 application.yml co
-- hibernate.jdbc.time_zone=UTC -> Hibernate bind LocalTime qua
-- java.sql.Time kieu cu -> java.sql.Time.valueOf() dung offset LICH SU
-- 1970 cua Asia/Ho_Chi_Minh (UTC+8, mai 1975 VN moi doi UTC+7) -> moi
-- LocalTime ghi xuong Postgres bi lech DUNG -8h. Doc lai qua Hibernate tu
-- bu tru nguoc nen app chay dung nhieu nam, chi lo ra khi co thu doc GIA
-- TRI THO (VD CHECK chk_session_time) - dac biet tiet hoc bat dau truoc
-- ~08:00. Setting da bo 2026-08-20 -> ghi MOI da dung; migration nay dich
-- +8h cho DU LIEU CU o moi truong chua duoc fix tay.
--
-- Postgres: `TIME + INTERVAL '8 hours'` tu wrap mod 24h, khong loi. Voi
-- moi row do bug -8h sinh ra, +8h dao nguoc chinh xac ve gia tri dung
-- (end_time > start_time van giu -> khong vi pham chk_session_time cua
-- class_sessions).
--
-- GUARD (bat buoc): moi UPDATE co `WHERE lower('${applyLocaltime8hShift}')
-- = 'true'`. Placeholder mac dinh 'false' (xem application.yml) ->
-- `WHERE false` -> 0 row, no-op. Muc dich:
--   * LOCAL/dev  - da fix tay 2026-08-20 -> KHONG dich lai (tranh +16h).
--   * CI         - container moi, 0 row -> vo hai du chay hay khong.
--   * STAGING/PROD co DU LIEU CU CHUA fix -> ops set env
--     APPLY_LOCALTIME_8H_SHIFT=true cho DUNG 1 lan deploy chay V159, roi
--     tra lai false. Xem CONTRIBUTING.md muc "Data-fix LocalTime".
-- 5 bang co cot LocalTime: class_sessions, session_periods,
-- site_period_templates, shifts, leave_requests (start/end nullable).
-- =====================================================================

UPDATE class_sessions
   SET start_time = start_time + INTERVAL '8 hours',
       end_time   = end_time   + INTERVAL '8 hours'
 WHERE lower('${applyLocaltime8hShift}') = 'true';

UPDATE session_periods
   SET start_time = start_time + INTERVAL '8 hours',
       end_time   = end_time   + INTERVAL '8 hours'
 WHERE lower('${applyLocaltime8hShift}') = 'true';

UPDATE site_period_templates
   SET start_time = start_time + INTERVAL '8 hours',
       end_time   = end_time   + INTERVAL '8 hours'
 WHERE lower('${applyLocaltime8hShift}') = 'true';

UPDATE shifts
   SET check_in_time  = check_in_time  + INTERVAL '8 hours',
       check_out_time = check_out_time + INTERVAL '8 hours'
 WHERE lower('${applyLocaltime8hShift}') = 'true';

UPDATE leave_requests
   SET start_time = start_time + INTERVAL '8 hours',
       end_time   = end_time   + INTERVAL '8 hours'
 WHERE lower('${applyLocaltime8hShift}') = 'true'
   AND (start_time IS NOT NULL OR end_time IS NOT NULL);
