package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.SchoolClass;

import java.util.Collection;
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
            AND (:academicYearId IS NULL OR c.academicYear.id = :academicYearId)
            AND (:restrictSites = FALSE OR c.site.id IN :allowedSiteIds)
            ORDER BY c.startDate DESC
            """)
    List<SchoolClass> search(@Param("siteId") Long siteId,
                              @Param("curriculumId") Long curriculumId,
                              @Param("classCategory") String classCategory,
                              @Param("academicYearId") Long academicYearId,
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
            AND (:academicYearId IS NULL OR c.academicYear.id = :academicYearId)
            AND (:restrictSites = FALSE OR c.site.id IN :allowedSiteIds)
            ORDER BY c.startDate DESC
            """)
    List<SchoolClass> searchByQuery(@Param("query") String query,
                                     @Param("siteId") Long siteId,
                                     @Param("curriculumId") Long curriculumId,
                                     @Param("classCategory") String classCategory,
                                     @Param("academicYearId") Long academicYearId,
                                     @Param("restrictSites") boolean restrictSites,
                                     @Param("allowedSiteIds") List<Long> allowedSiteIds);

    long countByCurriculumIdAndStatus(Long curriculumId, SchoolClass.Status status);

    /**
     * V203 — số liệu tổng quan cho dashboard Trưởng phòng đào tạo (bổ sung ngoài SDD gốc, xác nhận
     * 2026-09-30): số lớp theo trạng thái chưa kết thúc, số học sinh đang học (ghi danh ACTIVE ở lớp
     * OPEN_ENROLLMENT/IN_PROGRESS) và số giáo viên đang phụ trách các lớp đó. Tham số "không lọc":
     * siteId = 0, restrictSites = FALSE.
     */
    @Query(value = """
            SELECT COUNT(*) FILTER (WHERE c.status = 'PLANNED') AS plannedClasses,
                   COUNT(*) FILTER (WHERE c.status = 'OPEN_ENROLLMENT') AS openEnrollmentClasses,
                   COUNT(*) FILTER (WHERE c.status = 'IN_PROGRESS') AS inProgressClasses,
                   (SELECT COUNT(DISTINCT ce.student_id)
                    FROM class_enrollments ce
                    JOIN classes c2 ON c2.id = ce.class_id AND c2.deleted_at IS NULL
                    WHERE ce.status = 'ACTIVE'
                      AND c2.status IN ('OPEN_ENROLLMENT', 'IN_PROGRESS')
                      AND (:siteId = 0 OR c2.site_id = :siteId)
                      AND (:restrictSites = FALSE OR c2.site_id IN (:siteIds))) AS activeStudents,
                   (SELECT COUNT(DISTINCT ct.teacher_user_id)
                    FROM class_teachers ct
                    JOIN classes c3 ON c3.id = ct.class_id AND c3.deleted_at IS NULL
                    WHERE ct.assigned_to IS NULL
                      AND c3.status IN ('OPEN_ENROLLMENT', 'IN_PROGRESS')
                      AND (:siteId = 0 OR c3.site_id = :siteId)
                      AND (:restrictSites = FALSE OR c3.site_id IN (:siteIds))) AS activeTeachers
            FROM classes c
            WHERE c.deleted_at IS NULL
              AND (:siteId = 0 OR c.site_id = :siteId)
              AND (:restrictSites = FALSE OR c.site_id IN (:siteIds))
            """, nativeQuery = true)
    AcademicOverviewCounts countAcademicOverview(@Param("siteId") long siteId,
                                                 @Param("restrictSites") boolean restrictSites,
                                                 @Param("siteIds") Collection<Long> siteIds);

    interface AcademicOverviewCounts {
        Long getPlannedClasses();
        Long getOpenEnrollmentClasses();
        Long getInProgressClasses();
        Long getActiveStudents();
        Long getActiveTeachers();
    }
}
