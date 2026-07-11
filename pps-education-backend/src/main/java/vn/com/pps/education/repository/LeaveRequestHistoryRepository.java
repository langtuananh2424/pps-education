package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LeaveRequestHistory;

import java.util.List;

public interface LeaveRequestHistoryRepository extends JpaRepository<LeaveRequestHistory, Long> {
    List<LeaveRequestHistory> findByLeaveRequestIdOrderByCreatedAtDesc(Long leaveRequestId);
}
