package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    boolean existsByParentDepartmentId(Long parentDepartmentId);

    /** UC-06: các phòng ban mà 1 user làm trưởng phòng (head_user_id) — dùng cho phạm vi giao việc. */
    List<Department> findByHeadUserId(Long headUserId);
}
