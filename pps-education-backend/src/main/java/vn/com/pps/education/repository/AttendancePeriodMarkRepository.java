package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AttendancePeriodMark;

import java.util.List;
import java.util.Optional;

public interface AttendancePeriodMarkRepository extends JpaRepository<AttendancePeriodMark, Long> {
    List<AttendancePeriodMark> findByAttendanceMarkId(Long attendanceMarkId);
    Optional<AttendancePeriodMark> findByAttendanceMarkIdAndSessionPeriodId(Long attendanceMarkId, Long sessionPeriodId);

    /** academic.attendance.delete (UC-15) — xóa period marks của mọi mark thuộc 1 buổi điểm danh (con của attendance_marks). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM AttendancePeriodMark pm
            WHERE pm.attendanceMark.id IN (SELECT m.id FROM AttendanceMark m WHERE m.attendanceSession.id = :attendanceSessionId)
            """)
    void deleteByAttendanceSessionId(@Param("attendanceSessionId") Long attendanceSessionId);
}
