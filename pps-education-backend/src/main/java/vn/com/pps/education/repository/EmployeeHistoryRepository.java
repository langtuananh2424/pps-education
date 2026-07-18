package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EmployeeHistory;

import java.util.List;

public interface EmployeeHistoryRepository extends JpaRepository<EmployeeHistory, Long> {
    List<EmployeeHistory> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
