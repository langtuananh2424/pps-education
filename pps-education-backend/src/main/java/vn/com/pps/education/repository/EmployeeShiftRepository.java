package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EmployeeShift;

import java.util.List;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {
    /**
     * V124 (2026-08-14): 1 nhân sự có thể có NHIỀU ca active song song (VD "T7
     * xen kẽ" = 2 ca weekParity ODD/EVEN cùng active) -- không còn Optional.
     */
    List<EmployeeShift> findByEmployeeIdAndEffectiveToIsNull(Long employeeId);

    List<EmployeeShift> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
}
