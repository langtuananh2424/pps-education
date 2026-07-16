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

    /** UC-29 Portal trường liên kết: phạm vi lớp thuộc điểm trường phụ trách. */
    List<SchoolClass> findBySiteIdAndDeletedAtIsNull(Long siteId);

    /**
     * Dropdown lọc lớp theo trường (site) + chương trình (curriculum/classCategory),
     * không kèm text query. `restrictSites`/`allowedSiteIds`: giới hạn theo site
     * giáo viên được gán qua site_teachers khi actor không có quyền
     * academic.class.manage (Service tự tính, xem ClassService.resolveAllowedSiteIds)
     * — luôn truyền 1 list cụ thể (không bao giờ null/rỗng) để tránh vấn đề
     * Hibernate không xử lý được tham số IN rỗng/null.
     */
    @Query("""
            SELECT c FROM SchoolClass c
            WHERE c.deletedAt IS NULL
            AND (:siteId IS NULL OR c.site.id = :siteId)
            AND (:curriculumId IS NULL OR c.curriculum.id = :curriculumId)
            AND (:classCategory IS NULL OR c.classCategory = :classCategory)
            AND (:restrictSites = FALSE OR c.site.id IN :allowedSiteIds)
            ORDER BY c.startDate DESC
            """)
    List<SchoolClass> search(@Param("siteId") Long siteId,
                              @Param("curriculumId") Long curriculumId,
                              @Param("classCategory") String classCategory,
                              @Param("restrictSites") boolean restrictSites,
                              @Param("allowedSiteIds") List<Long> allowedSiteIds);

    // :query luôn non-null/non-blank ở đây (Service tự tách nhánh) — tránh lỗi
    // Postgres không suy được kiểu tham số NULL lồng trong LOWER/CONCAT (bytea).
    @Query("""
            SELECT c FROM SchoolClass c
            WHERE c.deletedAt IS NULL
            AND (LOWER(c.classCode) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:siteId IS NULL OR c.site.id = :siteId)
            AND (:curriculumId IS NULL OR c.curriculum.id = :curriculumId)
            AND (:classCategory IS NULL OR c.classCategory = :classCategory)
            AND (:restrictSites = FALSE OR c.site.id IN :allowedSiteIds)
            ORDER BY c.startDate DESC
            """)
    List<SchoolClass> searchByQuery(@Param("query") String query,
                                     @Param("siteId") Long siteId,
                                     @Param("curriculumId") Long curriculumId,
                                     @Param("classCategory") String classCategory,
                                     @Param("restrictSites") boolean restrictSites,
                                     @Param("allowedSiteIds") List<Long> allowedSiteIds);

    long countByCurriculumIdAndStatus(Long curriculumId, SchoolClass.Status status);
}
