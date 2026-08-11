package vn.com.pps.education.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AttendanceMark;

import java.util.List;
import java.util.Optional;

public interface AttendanceMarkRepository extends JpaRepository<AttendanceMark, Long> {
    List<AttendanceMark> findByAttendanceSessionId(Long attendanceSessionId);
    Optional<AttendanceMark> findByAttendanceSessionIdAndStudentId(Long attendanceSessionId, Long studentId);
    List<AttendanceMark> findByStudentId(Long studentId);

    /** academic.attendance.delete (UC-15) — xóa marks của 1 buổi điểm danh (sau khi đã xóa period marks + history). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AttendanceMark m WHERE m.attendanceSession.id = :attendanceSessionId")
    void deleteByAttendanceSessionId(@Param("attendanceSessionId") Long attendanceSessionId);

    /** UC-25 Portal Phụ huynh — chuyên cần: attendance_marks join class_sessions theo lớp (SDD). */
    @Query("""
            SELECT m FROM AttendanceMark m
            WHERE m.student.id = :studentId
            AND m.attendanceSession.classSession.schoolClass.id = :classId
            ORDER BY m.attendanceSession.classSession.sessionDate DESC
            """)
    List<AttendanceMark> findByStudentIdAndClassId(@Param("studentId") Long studentId, @Param("classId") Long classId);

    /**
     * Bổ sung ngoài SDD gốc — StudentProfileService (FR-REP-04): JOIN FETCH
     * buổi điểm danh/buổi học/lớp để tránh N+1 khi gộp điểm danh của 1 học
     * sinh qua mọi lớp. {@code pageable} giới hạn số buổi gần nhất (tránh
     * quét toàn bộ lịch sử nhiều năm trong 1 lần) — sắp DESC theo ngày buổi
     * học nên trang đầu (offset 0) luôn là các buổi gần nhất.
     */
    @Query("""
            SELECT m FROM AttendanceMark m
            JOIN FETCH m.attendanceSession asess
            JOIN FETCH asess.classSession cs
            JOIN FETCH cs.schoolClass sc
            WHERE m.student.id = :studentId
            ORDER BY cs.sessionDate DESC
            """)
    List<AttendanceMark> findByStudentIdWithContext(@Param("studentId") Long studentId, Pageable pageable);
}
