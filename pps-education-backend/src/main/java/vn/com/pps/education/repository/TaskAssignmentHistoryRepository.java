package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TaskAssignmentHistory;

public interface TaskAssignmentHistoryRepository extends JpaRepository<TaskAssignmentHistory, Long> {

    /** TaskSchedulerService (dọn CANCELLED) — xóa history của mọi assignment thuộc 1 task (FK, cháu của tasks). */
    void deleteByTaskAssignment_Task_Id(Long taskId);
}
