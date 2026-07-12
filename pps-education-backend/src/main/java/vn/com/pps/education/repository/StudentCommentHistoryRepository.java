package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentCommentHistory;

public interface StudentCommentHistoryRepository extends JpaRepository<StudentCommentHistory, Long> {
}
