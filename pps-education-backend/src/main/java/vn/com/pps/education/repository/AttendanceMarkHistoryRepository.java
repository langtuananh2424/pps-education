package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AttendanceMarkHistory;

public interface AttendanceMarkHistoryRepository extends JpaRepository<AttendanceMarkHistory, Long> {

    /** academic.attendance.delete (UC-15) — xóa history của mọi mark thuộc 1 buổi điểm danh (con của attendance_marks). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM AttendanceMarkHistory h
            WHERE h.attendanceMark.id IN (SELECT m.id FROM AttendanceMark m WHERE m.attendanceSession.id = :attendanceSessionId)
            """)
    void deleteByAttendanceSessionId(@Param("attendanceSessionId") Long attendanceSessionId);
}
