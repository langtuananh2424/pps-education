package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SessionPeriodHistory;

public interface SessionPeriodHistoryRepository extends JpaRepository<SessionPeriodHistory, Long> {

    /**
     * Xoá lịch sử của các tiết thuộc 1 buổi học (bổ sung ngoài SDD gốc,
     * xác nhận 2026-08-20) — bắt buộc gọi TRƯỚC khi xoá session_periods
     * trong ClassSessionService.updateAssignment, vì
     * session_periods_history.session_period_id là FK NOT NULL không có
     * ON DELETE CASCADE (V14) — xoá period còn lịch sử trỏ tới sẽ vi phạm
     * FK. Chấp nhận mất lịch sử cấp-tiết của lần sửa cũ (audit cấp buổi
     * học vẫn còn nguyên ở class_sessions_history UPDATED).
     */
    void deleteBySessionPeriodClassSessionId(Long classSessionId);
}
