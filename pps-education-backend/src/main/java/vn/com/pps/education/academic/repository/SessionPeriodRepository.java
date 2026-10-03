package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.academic.domain.SessionPeriod;
import vn.com.pps.education.facility.domain.SitePeriodTemplate;

import java.time.LocalDate;
import java.util.List;

public interface SessionPeriodRepository extends JpaRepository<SessionPeriod, Long> {
    List<SessionPeriod> findByClassSessionIdOrderByPeriodNumber(Long classSessionId);

    void deleteByClassSessionId(Long classSessionId);

    /**
     * Chặn xoá site_period_templates đang bị buổi SCHEDULED tương lai tham chiếu — xem SitePeriodTemplateService.
     * Tính cả IN_PROGRESS (buổi đang dạy — UC-48 A5, xác nhận 2026-10-01): trước đây buổi đang dạy vẫn ở
     * SCHEDULED nên tự được tính.
     */
    @Query("""
            SELECT COUNT(sp) > 0 FROM SessionPeriod sp
            WHERE sp.classSession.schoolClass.site.id = :siteId
              AND sp.dayPart = :dayPart
              AND sp.periodNumber = :periodNumber
              AND sp.classSession.status IN (
                  vn.com.pps.education.academic.domain.ClassSession.Status.SCHEDULED,
                  vn.com.pps.education.academic.domain.ClassSession.Status.IN_PROGRESS
              )
              AND sp.classSession.sessionDate >= :fromDate
            """)
    boolean existsFutureScheduledUsage(@Param("siteId") Long siteId, @Param("dayPart") SitePeriodTemplate.DayPart dayPart,
                                        @Param("periodNumber") int periodNumber, @Param("fromDate") LocalDate fromDate);

    /**
     * Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20 — số tiết THỰC TẾ đã dạy của từng lớp
     * trong 1 điểm trường, theo khoảng [fromDate, toDate] (tuần/tháng/kỳ/năm tuỳ FE truyền vào). "Thực
     * tế" = số session_periods của các class_sessions đã COMPLETED (xác nhận với người dùng 2026-09-30/
     * 2026-10-01 — UC-48 A5: buổi tự chuyển COMPLETED khi qua giờ kết thúc). Trước đây đếm mọi buổi
     * không CANCELLED/RESCHEDULED nên cộng cả buổi SCHEDULED chưa diễn ra. Buổi đã dời: buổi cũ
     * RESCHEDULED không tính, buổi mới tính theo ngày mới khi qua giờ kết thúc; buổi đã diễn ra nhưng bị
     * hủy sau (UC-48 A6) thành CANCELLED nên cũng không tính.
     */
    @Query("""
            SELECT sp.classSession.schoolClass.id AS classId, COUNT(sp) AS periodCount
            FROM SessionPeriod sp
            WHERE sp.classSession.schoolClass.site.id = :siteId
              AND sp.classSession.sessionDate BETWEEN :fromDate AND :toDate
              AND sp.classSession.status = vn.com.pps.education.academic.domain.ClassSession.Status.COMPLETED
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
