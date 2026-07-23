package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TaskAssignment;

import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    List<TaskAssignment> findByTaskId(Long taskId);

    List<TaskAssignment> findByAssigneeIdOrderByIdDesc(Long assigneeUserId);

    Optional<TaskAssignment> findByTaskIdAndAssigneeId(Long taskId, Long assigneeUserId);
}
