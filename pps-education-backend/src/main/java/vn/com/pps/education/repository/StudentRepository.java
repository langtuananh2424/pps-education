package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Student;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByIdAndDeletedAtIsNull(Long id);

    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByStudentCode(String studentCode);

    long countByStudentCodeStartingWith(String prefix);

    /**
     * Lọc theo điểm trường (siteId) và/hoặc giới hạn theo (các) điểm trường
     * actor được phép truy cập (restrictSites/allowedSiteIds — Service tự
     * tính, xem StudentService.resolveAllowedSiteIds) — không kèm text
     * query. Luôn truyền 1 list cụ thể (không bao giờ null/rỗng) cho
     * allowedSiteIds, tránh vấn đề Hibernate không xử lý được tham số IN
     * null/rỗng.
     */
    @Query("""
            SELECT s FROM Student s JOIN s.user u
            WHERE s.deletedAt IS NULL
            AND (:siteId IS NULL OR s.primarySite.id = :siteId)
            AND (:restrictSites = FALSE OR s.primarySite.id IN :allowedSiteIds)
            ORDER BY u.fullName
            """)
    List<Student> search(@Param("siteId") Long siteId,
                          @Param("restrictSites") boolean restrictSites,
                          @Param("allowedSiteIds") List<Long> allowedSiteIds);

    // :query luôn non-null/non-blank ở đây (Service tự tách nhánh) — tránh lỗi
    // Postgres không suy được kiểu tham số NULL lồng trong LOWER/CONCAT (bytea).
    @Query("""
            SELECT s FROM Student s JOIN s.user u
            WHERE s.deletedAt IS NULL
            AND (LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:siteId IS NULL OR s.primarySite.id = :siteId)
            AND (:restrictSites = FALSE OR s.primarySite.id IN :allowedSiteIds)
            ORDER BY u.fullName
            """)
    List<Student> searchByQuery(@Param("query") String query,
                                 @Param("siteId") Long siteId,
                                 @Param("restrictSites") boolean restrictSites,
                                 @Param("allowedSiteIds") List<Long> allowedSiteIds);

    /** UC-35 Main Flow bước 3: kiểm tra trùng lặp theo họ tên + ngày sinh (không có CCCD trong schema). */
    @Query("""
            SELECT s FROM Student s JOIN s.user u
            WHERE s.deletedAt IS NULL AND s.dateOfBirth = :dateOfBirth
            AND LOWER(u.fullName) = LOWER(:fullName)
            """)
    List<Student> findByFullNameAndDateOfBirth(@Param("fullName") String fullName, @Param("dateOfBirth") java.time.LocalDate dateOfBirth);
}
