package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.QuestionHistory;

public interface QuestionHistoryRepository extends JpaRepository<QuestionHistory, Long> {
}
