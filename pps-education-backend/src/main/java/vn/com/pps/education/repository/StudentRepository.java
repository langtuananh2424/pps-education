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

    long countByStudentCodeStartingWith(String prefix);

    @Query("""
            SELECT s FROM Student s JOIN s.user u
            WHERE s.deletedAt IS NULL
            ORDER BY u.fullName
            """)
    List<Student> findAllActive();

    // :query luôn non-null/non-blank ở đây (Service tự tách nhánh) — tránh lỗi
    // Postgres không suy được kiểu tham số NULL lồng trong LOWER/CONCAT (bytea).
    @Query("""
            SELECT s FROM Student s JOIN s.user u
            WHERE s.deletedAt IS NULL
            AND (LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY u.fullName
            """)
    List<Student> searchByQuery(@Param("query") String query);

    /** UC-35 Main Flow bước 3: kiểm tra trùng lặp theo họ tên + ngày sinh (không có CCCD trong schema). */
    @Query("""
            SELECT s FROM Student s JOIN s.user u
            WHERE s.deletedAt IS NULL AND s.dateOfBirth = :dateOfBirth
            AND LOWER(u.fullName) = LOWER(:fullName)
            """)
    List<Student> findByFullNameAndDateOfBirth(@Param("fullName") String fullName, @Param("dateOfBirth") java.time.LocalDate dateOfBirth);
}
