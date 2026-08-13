package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.GradeEntry;

import java.util.List;
import java.util.Optional;

public interface GradeEntryRepository extends JpaRepository<GradeEntry, Long> {

    List<GradeEntry> findBySchoolClassIdAndGradeComponentIdOrderByStudentId(Long classId, Long gradeComponentId);

    Optional<GradeEntry> findBySchoolClassIdAndStudentIdAndGradeComponentId(Long classId, Long studentId, Long gradeComponentId);

    List<GradeEntry> findBySchoolClassIdAndStudentId(Long classId, Long studentId);

    /** UC-25/61 Portal Phụ huynh/Học sinh — bảng điểm: chỉ hiển thị bản ghi đã duyệt (V44, OFFICIAL). */
    List<GradeEntry> findBySchoolClassIdAndStudentIdAndStatusIn(Long classId, Long studentId, List<GradeEntry.Status> statuses);

    long countByGradeComponentId(Long gradeComponentId);

    @Query("""
            SELECT e FROM GradeEntry e
            WHERE e.status = :status
            AND e.schoolClass.site.id = :siteId
            ORDER BY e.enteredAt ASC
            """)
    List<GradeEntry> findByStatusAndSiteId(@Param("status") GradeEntry.Status status, @Param("siteId") Long siteId);

    /** UC-25 Portal trường liên kết (PartnerPortalService) — bảng điểm: chỉ hiển thị bản ghi đã duyệt (V44, OFFICIAL), theo site. */
    @Query("""
            SELECT e FROM GradeEntry e
            WHERE e.status IN :statuses
            AND e.schoolClass.site.id = :siteId
            ORDER BY e.enteredAt ASC
            """)
    List<GradeEntry> findByStatusInAndSiteId(@Param("statuses") List<GradeEntry.Status> statuses, @Param("siteId") Long siteId);

    /** UC-20 (mở rộng, bổ sung ngoài SDD gốc): HEAD_ACADEMIC xem danh sách chờ duyệt của MỌI site, không giới hạn theo site_managers. */
    List<GradeEntry> findByStatusOrderByEnteredAtAsc(GradeEntry.Status status);

    /** Bổ sung ngoài SDD gốc — StudentProfileService (FR-REP-04): JOIN FETCH thành phần điểm/lớp/kỳ học để tránh N+1 khi gộp điểm từng kỹ năng của 1 học sinh qua mọi lớp/kỳ. */
    @Query("""
            SELECT e FROM GradeEntry e
            JOIN FETCH e.gradeComponent gc
            JOIN FETCH e.schoolClass sc
            JOIN FETCH e.academicTerm t
            WHERE e.student.id = :studentId
            ORDER BY t.startDate ASC, e.evaluationType ASC
            """)
    List<GradeEntry> findByStudentIdWithContext(@Param("studentId") Long studentId);
}
