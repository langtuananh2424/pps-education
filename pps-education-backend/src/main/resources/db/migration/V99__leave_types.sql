-- Create leave_types table
CREATE TABLE leave_types (
  id SERIAL PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  label VARCHAR(100) NOT NULL,
  sort_order INTEGER NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default leave types
INSERT INTO leave_types (code, label, sort_order, is_active) VALUES
  ('ANNUAL', 'Nghỉ phép năm', 1, true),
  ('SICK', 'Nghỉ ốm', 2, true),
  ('UNPAID', 'Nghỉ không lương', 3, true),
  ('PERSONAL', 'Nghỉ việc riêng', 4, true),
  ('LATE', 'Đi trễ có lý do', 5, true),
  ('EARLY_LEAVE', 'Về sớm', 6, true);

-- Create index
CREATE INDEX idx_leave_types_is_active ON leave_types(is_active);
