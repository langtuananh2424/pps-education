package vn.com.pps.education.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.hr.domain.EmployeeHistory;

import java.util.List;
import java.util.Optional;

public interface EmployeeHistoryRepository extends JpaRepository<EmployeeHistory, Long> {
    List<EmployeeHistory> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /** Bản ghi lịch sử liền trước của cùng nhân sự — để hiển thị "giá trị cũ → mới" (V203). */
    Optional<EmployeeHistory> findFirstByEmployeeIdAndIdLessThanOrderByIdDesc(Long employeeId, Long id);
}
