package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.AttendanceRecordHistory;

import java.util.List;

public interface AttendanceRecordHistoryRepository extends JpaRepository<AttendanceRecordHistory, Long> {
    List<AttendanceRecordHistory> findByAttendanceRecordIdOrderByCreatedAtDesc(Long attendanceRecordId);
}
