package vn.com.pps.education.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.task.domain.TaskHistory;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    /** TaskSchedulerService (dọn CANCELLED) — xóa history của task trước khi xóa task (FK). */
    void deleteByTaskId(Long taskId);
}
