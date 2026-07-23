package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TaskComment;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    /** TaskSchedulerService (dọn CANCELLED) — xóa comment của task trước khi xóa task (FK). */
    void deleteByTaskId(Long taskId);
}
