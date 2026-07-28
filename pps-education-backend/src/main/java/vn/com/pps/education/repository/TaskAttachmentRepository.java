package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TaskAttachment;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskId(Long taskId);

    /** TaskSchedulerService (dọn CANCELLED) — xóa attachment của task trước khi xóa task (FK). */
    void deleteByTaskId(Long taskId);
}
