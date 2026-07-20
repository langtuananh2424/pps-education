package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Employee;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdAndDeletedAtIsNull(Long id);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByUserId(Long userId);

    List<Employee> findByUserIdIn(Collection<Long> userIds);

    /** DepartmentService#delete — có nhân sự trực thuộc (kể cả đã soft-delete, FK vẫn ràng buộc) thì không xóa được phòng ban. */
    boolean existsByDepartmentId(Long departmentId);

    /** PositionService#delete — có nhân sự mang chức vụ (kể cả đã soft-delete, FK vẫn ràng buộc) thì không xóa được chức vụ. */
    boolean existsByPositionId(Long positionId);

    /** PositionService#updateDefaultRoles (UC-52 bước 5) — backfill vai trò cho nhân sự đang giữ chức vụ vừa cấu hình lại. */
    List<Employee> findByPositionIdAndDeletedAtIsNull(Long positionId);

    /** :departmentId nullable — bổ sung filter theo phòng ban (trước đây không có, tham số bị FE truyền lên nhưng Controller không khai báo nên bị Spring MVC bỏ qua âm thầm). */
    @Query("""
            SELECT e FROM Employee e JOIN e.user u
            WHERE e.deletedAt IS NULL
            AND (:departmentId IS NULL OR e.department.id = :departmentId)
            ORDER BY u.fullName
            """)
    List<Employee> findAllActive(@Param("departmentId") Long departmentId);

    // :query luôn non-null/non-blank ở đây (Service tự tách nhánh) — tránh lỗi
    // Postgres không suy được kiểu tham số NULL lồng trong LOWER/CONCAT (bytea).
    @Query("""
            SELECT e FROM Employee e JOIN e.user u
            WHERE e.deletedAt IS NULL
            AND (LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:departmentId IS NULL OR e.department.id = :departmentId)
            ORDER BY u.fullName
            """)
    List<Employee> searchByQuery(@Param("query") String query, @Param("departmentId") Long departmentId);
}
