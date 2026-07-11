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

    long countByGradeComponentId(Long gradeComponentId);

    @Query("""
            SELECT e FROM GradeEntry e
            WHERE e.status = :status
            AND e.schoolClass.site.id = :siteId
            ORDER BY e.submittedAt ASC
            """)
    List<GradeEntry> findByStatusAndSiteId(@Param("status") GradeEntry.Status status, @Param("siteId") Long siteId);
}
