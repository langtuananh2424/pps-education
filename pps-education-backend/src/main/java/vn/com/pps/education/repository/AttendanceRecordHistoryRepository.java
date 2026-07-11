package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AttendanceRecordHistory;

import java.util.List;

public interface AttendanceRecordHistoryRepository extends JpaRepository<AttendanceRecordHistory, Long> {
    List<AttendanceRecordHistory> findByAttendanceRecordIdOrderByCreatedAtDesc(Long attendanceRecordId);
}
