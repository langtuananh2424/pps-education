package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LeaveRequest;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByStatusAndCurrentApproverId(LeaveRequest.Status status, Long currentApproverId);

    List<LeaveRequest> findByStatusAndCurrentApproverIsNull(LeaveRequest.Status status);
}
