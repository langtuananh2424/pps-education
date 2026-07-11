package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LeaveRequestApproval;

import java.util.List;
import java.util.Optional;

public interface LeaveRequestApprovalRepository extends JpaRepository<LeaveRequestApproval, Long> {

    Optional<LeaveRequestApproval> findByLeaveRequestIdAndStepOrder(Long leaveRequestId, int stepOrder);

    List<LeaveRequestApproval> findByLeaveRequestIdOrderByStepOrder(Long leaveRequestId);
}
