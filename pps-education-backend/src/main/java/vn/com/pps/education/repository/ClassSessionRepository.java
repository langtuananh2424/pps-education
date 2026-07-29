package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ClassSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findBySchoolClassIdOrderBySessionDateAsc(Long classId);

    /** UC-21 mở rộng (BTVN theo buổi, V55): buổi học liền TRƯỚC 1 buổi, cùng lớp — tra lại FK "sẽ giao gì" đã ghi ở dòng student_comments của buổi trước. */
    Optional<ClassSession> findFirstBySchoolClassIdAndSessionDateLessThanOrderBySessionDateDescIdDesc(Long classId, LocalDate sessionDate);

    /** UC-09 A12/A13 (Chấm công GV — cửa sổ theo lịch dạy): tiết dạy trong ngày của 1 GV. */
    List<ClassSession> findByPrimaryTeacherIdAndSessionDateAndStatusNotIn(
            Long primaryTeacherId, LocalDate sessionDate, List<ClassSession.Status> excludedStatuses);

    /**
     * FR-FAC-03 — kiểm tra trùng phòng: cùng room_id, cùng ngày, khoảng
     * thời gian giao nhau, status không phải CANCELLED/RESCHEDULED, loại
     * trừ chính session đang sửa (editingSessionId=null khi tạo mới).
     * Chỉ gọi khi room.isFlexible()=false (Service tự lọc trước).
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.room.id = :roomId
            AND cs.sessionDate = :date
            AND cs.status NOT IN (:excludedStatuses)
            AND :startTime < cs.endTime AND :endTime > cs.startTime
            AND (:editingSessionId IS NULL OR cs.id <> :editingSessionId)
            """)
    List<ClassSession> findOverlappingInRoom(@Param("roomId") Long roomId, @Param("date") LocalDate date,
                                              @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                                              @Param("editingSessionId") Long editingSessionId,
                                              @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);

    /**
     * UC-58: "Lịch của tôi" — mọi buổi học của 1 Giáo viên qua MỌI lớp,
     * lọc theo khoảng ngày tùy chọn (null = không giới hạn). Tận dụng
     * index có sẵn idx_class_sessions_teacher_date (primary_teacher_id,
     * session_date DESC).
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.primaryTeacher.id = :teacherId
            AND (cast(:fromDate as date) IS NULL OR cs.sessionDate >= :fromDate)
            AND (cast(:toDate as date) IS NULL OR cs.sessionDate <= :toDate)
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findByPrimaryTeacherAndDateRange(@Param("teacherId") Long teacherId,
                                                         @Param("fromDate") LocalDate fromDate,
                                                         @Param("toDate") LocalDate toDate);

    /**
     * UC-59: "Lịch học của tôi" (Học sinh) — mọi buổi học của các lớp
     * học sinh đang ghi danh (classIds đã lọc ACTIVE ở Service), lọc theo
     * khoảng ngày tùy chọn (null = không giới hạn). Đối xứng với
     * findByPrimaryTeacherAndDateRange (UC-58) — khác ở chỗ lọc theo
     * nhiều classId thay vì 1 teacherId.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.id IN (:classIds)
            AND (cast(:fromDate as date) IS NULL OR cs.sessionDate >= :fromDate)
            AND (cast(:toDate as date) IS NULL OR cs.sessionDate <= :toDate)
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findBySchoolClassIdInAndDateRange(@Param("classIds") List<Long> classIds,
                                                          @Param("fromDate") LocalDate fromDate,
                                                          @Param("toDate") LocalDate toDate);

    /**
     * "Buổi N" hiển thị FE: đếm số buổi đứng TRƯỚC buổi này (theo
     * sessionDate rồi id), kể cả CANCELLED — bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-07-29.
     */
    @Query("""
            SELECT COUNT(s) FROM ClassSession s
            WHERE s.schoolClass.id = :classId
            AND (s.sessionDate < :sessionDate OR (s.sessionDate = :sessionDate AND s.id < :sessionId))
            """)
    long countEarlierSessions(@Param("classId") Long classId, @Param("sessionDate") LocalDate sessionDate,
                               @Param("sessionId") Long sessionId);

    /** Buổi học hôm nay của 1 lớp (tab Nhận xét, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29). */
    List<ClassSession> findBySchoolClassIdAndSessionDate(Long classId, LocalDate sessionDate);
}
