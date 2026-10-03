package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.QuestionHistory;

public interface QuestionHistoryRepository extends JpaRepository<QuestionHistory, Long> {
}
