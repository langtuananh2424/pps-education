package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdAndDeletedAtIsNull(Long id);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByUserId(Long userId);

    @Query("""
            SELECT e FROM Employee e JOIN e.user u
            WHERE e.deletedAt IS NULL
            ORDER BY u.fullName
            """)
    List<Employee> findAllActive();

    // :query luôn non-null/non-blank ở đây (Service tự tách nhánh) — tránh lỗi
    // Postgres không suy được kiểu tham số NULL lồng trong LOWER/CONCAT (bytea).
    @Query("""
            SELECT e FROM Employee e JOIN e.user u
            WHERE e.deletedAt IS NULL
            AND (LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY u.fullName
            """)
    List<Employee> searchByQuery(@Param("query") String query);
}
