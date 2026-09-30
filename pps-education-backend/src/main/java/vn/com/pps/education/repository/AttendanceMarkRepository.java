package vn.com.pps.education.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AttendanceMark;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * UC-74 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — điểm danh nhiều buổi × nhiều học sinh
     * trong 1 truy vấn (chuyên cần cho trợ lý nhận xét), kèm id buổi học để nhóm theo buổi.
     */
    @Query("""
            SELECT m FROM AttendanceMark m
            JOIN FETCH m.attendanceSession asess
            WHERE asess.classSession.id IN :classSessionIds
            AND m.student.id IN :studentIds
            """)
    List<AttendanceMark> findByClassSessionIdInAndStudentIdIn(@Param("classSessionIds") java.util.Collection<Long> classSessionIds,
                                                              @Param("studentIds") java.util.Collection<Long> studentIds);

    /**
     * V203 — số lượt điểm danh học sinh theo trạng thái trong [fromDate, toDate] (buổi không huỷ/không
     * dời), cho tỷ lệ chuyên cần trên dashboard Trưởng phòng đào tạo. Tham số "không lọc": siteId = 0,
     * restrictSites = FALSE.
     */
    @Query(value = """
            SELECT am.status AS status, COUNT(*) AS markCount
            FROM attendance_marks am
            JOIN attendance_sessions ats ON ats.id = am.attendance_session_id
            JOIN class_sessions cs ON cs.id = ats.class_session_id
            JOIN classes c ON c.id = cs.class_id AND c.deleted_at IS NULL
            WHERE cs.session_date BETWEEN :fromDate AND :toDate
              AND cs.status NOT IN ('CANCELLED', 'RESCHEDULED')
              AND (:siteId = 0 OR c.site_id = :siteId)
              AND (:restrictSites = FALSE OR c.site_id IN (:siteIds))
            GROUP BY am.status
            """, nativeQuery = true)
    List<AttendanceStatusCount> countMarksByStatus(@Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate,
                                                   @Param("siteId") long siteId,
                                                   @Param("restrictSites") boolean restrictSites,
                                                   @Param("siteIds") Collection<Long> siteIds);

    /**
     * V203 — tổng hợp chuyên cần từng học sinh của 1 lớp trong [fromDate, toDate] (buổi không huỷ/không
     * dời, chỉ buổi đã có điểm danh), dùng cho file Excel "Tổng hợp chuyên cần".
     */
    @Query(value = """
            SELECT am.student_id AS studentId,
                   COUNT(*) AS totalMarks,
                   COUNT(*) FILTER (WHERE am.status = 'PRESENT') AS presentCount,
                   COUNT(*) FILTER (WHERE am.status = 'LATE') AS lateCount,
                   COUNT(*) FILTER (WHERE am.status = 'EARLY_LEAVE') AS earlyLeaveCount,
                   COUNT(*) FILTER (WHERE am.status = 'EXCUSED') AS excusedCount,
                   COUNT(*) FILTER (WHERE am.status = 'ABSENT') AS absentCount
            FROM attendance_marks am
            JOIN attendance_sessions ats ON ats.id = am.attendance_session_id
            JOIN class_sessions cs ON cs.id = ats.class_session_id
            WHERE cs.class_id = :classId
              AND cs.session_date BETWEEN :fromDate AND :toDate
              AND cs.status NOT IN ('CANCELLED', 'RESCHEDULED')
            GROUP BY am.student_id
            """, nativeQuery = true)
    List<StudentAttendanceSummary> summarizeByStudentForClass(@Param("classId") Long classId,
                                                              @Param("fromDate") LocalDate fromDate,
                                                              @Param("toDate") LocalDate toDate);

    /** V203 — số buổi của lớp trong [fromDate, toDate] đã có điểm danh (buổi không huỷ/không dời). */
    @Query(value = """
            SELECT COUNT(DISTINCT ats.id)
            FROM attendance_sessions ats
            JOIN class_sessions cs ON cs.id = ats.class_session_id
            WHERE cs.class_id = :classId
              AND cs.session_date BETWEEN :fromDate AND :toDate
              AND cs.status NOT IN ('CANCELLED', 'RESCHEDULED')
            """, nativeQuery = true)
    long countAttendanceSessionsForClass(@Param("classId") Long classId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    interface AttendanceStatusCount {
        String getStatus();
        Long getMarkCount();
    }

    interface StudentAttendanceSummary {
        Long getStudentId();
        Long getTotalMarks();
        Long getPresentCount();
        Long getLateCount();
        Long getEarlyLeaveCount();
        Long getExcusedCount();
        Long getAbsentCount();
    }
}
