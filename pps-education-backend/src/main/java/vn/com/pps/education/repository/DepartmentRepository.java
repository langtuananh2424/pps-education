package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
