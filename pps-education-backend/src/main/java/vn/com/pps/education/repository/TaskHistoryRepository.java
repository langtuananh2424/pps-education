package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TaskHistory;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {
}
