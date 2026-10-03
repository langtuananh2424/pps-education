package vn.com.pps.education.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.student.domain.ParentHistory;

import java.util.List;

public interface ParentHistoryRepository extends JpaRepository<ParentHistory, Long> {
    List<ParentHistory> findByParentIdOrderByCreatedAtDesc(Long parentId);
}
