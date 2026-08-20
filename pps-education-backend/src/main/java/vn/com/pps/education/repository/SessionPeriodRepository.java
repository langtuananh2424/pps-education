package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.SessionPeriod;
import vn.com.pps.education.domain.SitePeriodTemplate;

import java.time.LocalDate;
import java.util.List;

public interface SessionPeriodRepository extends JpaRepository<SessionPeriod, Long> {
    List<SessionPeriod> findByClassSessionIdOrderByPeriodNumber(Long classSessionId);

    void deleteByClassSessionId(Long classSessionId);

    /** Chặn xoá site_period_templates đang bị buổi SCHEDULED tương lai tham chiếu — xem SitePeriodTemplateService. */
    @Query("""
            SELECT COUNT(sp) > 0 FROM SessionPeriod sp
            WHERE sp.classSession.schoolClass.site.id = :siteId
              AND sp.dayPart = :dayPart
              AND sp.periodNumber = :periodNumber
              AND sp.classSession.status = vn.com.pps.education.domain.ClassSession.Status.SCHEDULED
              AND sp.classSession.sessionDate >= :fromDate
            """)
    boolean existsFutureScheduledUsage(@Param("siteId") Long siteId, @Param("dayPart") SitePeriodTemplate.DayPart dayPart,
                                        @Param("periodNumber") int periodNumber, @Param("fromDate") LocalDate fromDate);

    /**
     * Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20 — số tiết THỰC TẾ đã dạy của từng lớp
     * trong 1 điểm trường, theo khoảng [fromDate, toDate] (tuần/tháng/kỳ/năm tuỳ FE truyền vào). "Thực
     * tế" = số session_periods của các class_sessions KHÔNG CANCELLED/RESCHEDULED (cùng quy ước loại
     * trừ đã dùng xuyên suốt repo — VD ClassSessionRepository#findOverlappingInRoom), khớp đúng số tiết
     * học sinh/giáo viên thật sự có mặt, không đếm buổi đã huỷ hoặc đã dời sang buổi khác.
     */
    @Query("""
            SELECT sp.classSession.schoolClass.id AS classId, COUNT(sp) AS periodCount
            FROM SessionPeriod sp
            WHERE sp.classSession.schoolClass.site.id = :siteId
              AND sp.classSession.sessionDate BETWEEN :fromDate AND :toDate
              AND sp.classSession.status NOT IN (
                  vn.com.pps.education.domain.ClassSession.Status.CANCELLED,
                  vn.com.pps.education.domain.ClassSession.Status.RESCHEDULED
              )
              AND (:classId IS NULL OR sp.classSession.schoolClass.id = :classId)
            GROUP BY sp.classSession.schoolClass.id
            """)
    List<ClassActualPeriodCount> countActualPeriodsBySite(@Param("siteId") Long siteId, @Param("fromDate") LocalDate fromDate,
                                                            @Param("toDate") LocalDate toDate, @Param("classId") Long classId);

    interface ClassActualPeriodCount {
        Long getClassId();
        Long getPeriodCount();
    }
}
