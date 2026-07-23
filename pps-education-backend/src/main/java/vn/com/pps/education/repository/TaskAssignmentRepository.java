package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TaskAssignment;

import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    List<TaskAssignment> findByTaskId(Long taskId);

    List<TaskAssignment> findByAssigneeIdOrderByIdDesc(Long assigneeUserId);

    Optional<TaskAssignment> findByTaskIdAndAssigneeId(Long taskId, Long assigneeUserId);

    /** TaskSchedulerService (dọn CANCELLED) — xóa assignment của task trước khi xóa task (FK). */
    void deleteByTaskId(Long taskId);
}
