package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.SchoolClass;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    Optional<SchoolClass> findByIdAndDeletedAtIsNull(Long id);

    Optional<SchoolClass> findByClassCode(String classCode);

    List<SchoolClass> findByDeletedAtIsNullOrderByStartDateDesc();

    @Query("""
            SELECT c FROM SchoolClass c
            WHERE c.deletedAt IS NULL
            AND (LOWER(c.classCode) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY c.startDate DESC
            """)
    List<SchoolClass> searchByQuery(@Param("query") String query);

    long countByCurriculumIdAndStatus(Long curriculumId, SchoolClass.Status status);
}
