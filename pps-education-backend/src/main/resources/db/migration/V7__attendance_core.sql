-- =====================================================================
-- V7: UC-09 (Chấm công) - shifts, work_calendar, attendance_records
-- =====================================================================

-- a) shifts -- Ca làm việc chuẩn
CREATE TABLE shifts (
    id                                 BIGSERIAL PRIMARY KEY,
    code                                VARCHAR(50) UNIQUE NOT NULL,
    name                                VARCHAR(200) NOT NULL,
    check_in_time                      TIME NOT NULL,
    check_out_time                     TIME NOT NULL,
    check_in_window_before_minutes     INT NOT NULL DEFAULT 30,
    check_in_window_after_minutes      INT NOT NULL DEFAULT 30,
    check_out_window_before_minutes    INT NOT NULL DEFAULT 30,
    check_out_window_after_minutes     INT NOT NULL DEFAULT 60,
    applies_to_weekdays                VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6', -- 1=T2...7=CN
    week_parity                        VARCHAR(10) NOT NULL DEFAULT 'ALL', -- ALL / ODD / EVEN
    is_active                          BOOLEAN NOT NULL DEFAULT TRUE,
    import_job_id                      BIGINT NULL REFERENCES import_jobs(id)
);

-- shifts_history -- SDD chỉ ghi "Có shifts_history", không định nghĩa cột;
-- áp dụng lại pattern JSONB diff-log đã dùng ở employees_history (V6).
CREATE TABLE shifts_history (
    id            BIGSERIAL PRIMARY KEY,
    shift_id      BIGINT NOT NULL REFERENCES shifts(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shifts_history_shift ON shifts_history(shift_id);

-- b) employee_shifts -- Gán ca cho nhân sự
CREATE TABLE employee_shifts (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT NOT NULL REFERENCES employees(id),
    shift_id          BIGINT NOT NULL REFERENCES shifts(id),
    effective_from    DATE NOT NULL,
    effective_to      DATE NULL, -- NULL = đang áp dụng
    import_job_id     BIGINT NULL REFERENCES import_jobs(id)
);
-- Ràng buộc: mỗi nhân sự 1 thời điểm chỉ 1 ca đang active (effective_to NULL)
CREATE UNIQUE INDEX idx_employee_shifts_active ON employee_shifts(employee_id)
    WHERE effective_to IS NULL;

-- c) work_calendar -- Lịch làm việc override theo ngày cụ thể
CREATE TABLE work_calendar (
    id                  BIGSERIAL PRIMARY KEY,
    calendar_date       DATE NOT NULL,
    day_type            VARCHAR(20) NOT NULL, -- WORKING / OFF / HOLIDAY / COMPENSATORY
    applies_to_scope    VARCHAR(20) NOT NULL DEFAULT 'ALL', -- ALL / SHIFT / EMPLOYEE
    shift_id            BIGINT NULL REFERENCES shifts(id), -- chỉ set khi scope=SHIFT
    employee_id         BIGINT NULL REFERENCES employees(id), -- chỉ set khi scope=EMPLOYEE
    description         VARCHAR(500) NULL,
    import_job_id       BIGINT NULL REFERENCES import_jobs(id),
    created_by          BIGINT NULL REFERENCES users(id)
);
CREATE INDEX idx_work_calendar_date ON work_calendar(calendar_date);

-- d) attendance_records -- Bản ghi chấm công
CREATE TABLE attendance_records (
    id                                BIGSERIAL PRIMARY KEY,
    employee_id                       BIGINT NOT NULL REFERENCES employees(id),
    work_date                         DATE NOT NULL,
    check_in_at                       TIMESTAMPTZ NULL,
    check_out_at                      TIMESTAMPTZ NULL,
    check_in_method                   VARCHAR(20) NULL, -- FINGERPRINT / FACE / GPS / MANUAL
    check_out_method                  VARCHAR(20) NULL,
    site_id                           BIGINT NULL REFERENCES sites(id),
    gps_location                      GEOGRAPHY(POINT, 4326) NULL,
    check_in_matched_source           VARCHAR(30) NULL, -- SHIFT / TEACHING_SCHEDULE / MANUAL_OVERRIDE
    check_in_matched_reference_id     BIGINT NULL, -- trỏ về shifts.id hoặc class_sessions.id tùy source
    status                            VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- NORMAL / LATE / EARLY_LEAVE / MISSING
    created_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(employee_id, work_date)
);

-- attendance_records_history -- snapshot JSONB (chỉnh trực tiếp bản ghi gốc,
-- không tách bảng điều chỉnh riêng - theo đúng ghi chú SDD).
CREATE TABLE attendance_records_history (
    id                    BIGSERIAL PRIMARY KEY,
    attendance_record_id  BIGINT NOT NULL REFERENCES attendance_records(id),
    changed_by            BIGINT NOT NULL REFERENCES users(id),
    action                VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details               JSONB NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_records_history_record ON attendance_records_history(attendance_record_id);

-- e) system_settings seed cho Chấm công (giá trị mặc định đúng theo
-- docs/sdd-groups/05-nhan-su.md > Chấm công - V4 đã ghi chú "sẽ bổ sung ở
-- Phase B khi làm Chấm công", đây chính là lúc đó)
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('attendance.gps_enabled', 'true', 'Cho phép chấm công GPS', 'ATTENDANCE'),
('attendance.fingerprint_enabled', 'true', 'Cho phép chấm công vân tay', 'ATTENDANCE'),
('attendance.face_enabled', 'true', 'Cho phép chấm công khuôn mặt', 'ATTENDANCE'),
('attendance.manual_when_all_disabled', 'true', 'Cho phép chấm công thủ công khi cả 3 phương thức trên bị tắt', 'ATTENDANCE'),
('attendance.gps_radius_meters', '200', 'Bán kính chấp nhận GPS (mét)', 'ATTENDANCE');
