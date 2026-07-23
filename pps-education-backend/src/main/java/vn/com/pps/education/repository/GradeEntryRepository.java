package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.GradeEntry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GradeEntryRepository extends JpaRepository<GradeEntry, Long> {

    List<GradeEntry> findBySchoolClassIdAndGradeComponentIdOrderByStudentId(Long classId, Long gradeComponentId);

    Optional<GradeEntry> findBySchoolClassIdAndStudentIdAndGradeComponentId(Long classId, Long studentId, Long gradeComponentId);

    List<GradeEntry> findBySchoolClassIdAndStudentId(Long classId, Long studentId);

<<<<<<< HEAD
    /** UC-25 Portal Phụ huynh — bảng điểm: grade_entries WHERE status=APPROVED (SDD). */
    List<GradeEntry> findBySchoolClassIdAndStudentIdAndStatus(Long classId, Long studentId, GradeEntry.Status status);
=======
    /** UC-25/61 Portal Phụ huynh/Học sinh — bảng điểm: mọi trạng thái đã công bố dự kiến trở lên (V43, khác DRAFT). */
    List<GradeEntry> findBySchoolClassIdAndStudentIdAndStatusNot(Long classId, Long studentId, GradeEntry.Status status);
>>>>>>> develop

    long countByGradeComponentId(Long gradeComponentId);

    @Query("""
            SELECT e FROM GradeEntry e
            WHERE e.status = :status
            AND e.schoolClass.site.id = :siteId
            ORDER BY e.submittedAt ASC
            """)
    List<GradeEntry> findByStatusAndSiteId(@Param("status") GradeEntry.Status status, @Param("siteId") Long siteId);

<<<<<<< HEAD
    /** UC-20 (mở rộng, bổ sung ngoài SDD gốc): HEAD_ACADEMIC xem hàng chờ duyệt của MỌI site, không giới hạn theo site_managers. */
    List<GradeEntry> findByStatusOrderBySubmittedAtAsc(GradeEntry.Status status);
=======
    /** UC-25 Portal trường liên kết (PartnerPortalService) — bảng điểm: mọi trạng thái đã công bố dự kiến trở lên (V43, khác DRAFT), theo site. */
    @Query("""
            SELECT e FROM GradeEntry e
            WHERE e.status != :excludedStatus
            AND e.schoolClass.site.id = :siteId
            ORDER BY e.enteredAt ASC
            """)
    List<GradeEntry> findByStatusNotAndSiteId(@Param("excludedStatus") GradeEntry.Status excludedStatus, @Param("siteId") Long siteId);

    /** UC-20 (mở rộng, bổ sung ngoài SDD gốc): HEAD_ACADEMIC xem danh sách chưa công bố của MỌI site, không giới hạn theo site_managers. */
    List<GradeEntry> findByStatusOrderByEnteredAtAsc(GradeEntry.Status status);

    /** GradeSchedulerService: mọi grade_entries còn DRAFT của 1 (lớp, kỳ đánh giá) — grade_component trỏ tới grade_period, không có cột trực tiếp. */
    @Query("""
            SELECT e FROM GradeEntry e
            WHERE e.schoolClass.id = :classId
            AND e.gradeComponent.gradePeriod.id = :gradePeriodId
            AND e.status = :status
            """)
    List<GradeEntry> findBySchoolClassIdAndGradePeriodIdAndStatus(@Param("classId") Long classId,
                                                                   @Param("gradePeriodId") Long gradePeriodId,
                                                                   @Param("status") GradeEntry.Status status);

    /** UC-62 A3 — GradeSchedulerService: hết hạn Y ngày phúc khảo kể từ publishedAt, tự động chuyển OFFICIAL bất kể còn PROVISIONAL_PUBLISHED hay APPEAL. */
    List<GradeEntry> findByStatusInAndPublishedAtBefore(List<GradeEntry.Status> statuses, OffsetDateTime cutoff);
>>>>>>> develop
}
