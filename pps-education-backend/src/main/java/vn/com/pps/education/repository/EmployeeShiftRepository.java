package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EmployeeShift;

import java.util.List;
import java.util.Optional;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {
    Optional<EmployeeShift> findByEmployeeIdAndEffectiveToIsNull(Long employeeId);

    List<EmployeeShift> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
}
