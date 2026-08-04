-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: khi hết hạn nộp BTVN (Bài tập
-- Ngữ pháp online / Video Ôn tập), hệ thống tự động gửi thông báo cho Giáo viên đã giao bài, kèm
-- % học sinh đã làm bài của cả lớp + % hoàn thành từng em. Job quét mỗi 5 phút (xem
-- HomeworkDeadlineSchedulerService), dùng cột này để không gửi trùng cho cùng 1 lần giao — mirror
-- attendance_marks.notified_parent_at (V45).

ALTER TABLE exercise_assignments ADD COLUMN teacher_notified_at TIMESTAMPTZ NULL;
ALTER TABLE review_video_assignments ADD COLUMN teacher_notified_at TIMESTAMPTZ NULL;

-- Tăng tốc job quét "due_at đã qua, chưa notify" mỗi 5 phút, chỉ xét bản ghi ACTIVE.
CREATE INDEX idx_exercise_assignments_deadline_scan
    ON exercise_assignments (due_at)
    WHERE status = 'ACTIVE' AND teacher_notified_at IS NULL;

CREATE INDEX idx_review_video_assignments_deadline_scan
    ON review_video_assignments (due_at)
    WHERE status = 'ACTIVE' AND teacher_notified_at IS NULL;
