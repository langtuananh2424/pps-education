package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AttendanceMarkHistory;

public interface AttendanceMarkHistoryRepository extends JpaRepository<AttendanceMarkHistory, Long> {
}
