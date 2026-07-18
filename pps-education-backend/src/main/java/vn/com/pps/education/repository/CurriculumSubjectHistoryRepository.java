package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumSubjectHistory;

import java.util.List;

public interface CurriculumSubjectHistoryRepository extends JpaRepository<CurriculumSubjectHistory, Long> {
    List<CurriculumSubjectHistory> findByCurriculumSubjectIdOrderByCreatedAtDesc(Long curriculumSubjectId);
}
