package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LessonHistory;

public interface LessonHistoryRepository extends JpaRepository<LessonHistory, Long> {
}
