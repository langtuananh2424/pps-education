package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LeaveType;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
  Optional<LeaveType> findByCode(String code);

  List<LeaveType> findByIsActiveTrueOrderBySortOrder();
}
