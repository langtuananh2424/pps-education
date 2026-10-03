package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.CurriculumSubjectHistory;

import java.util.List;

public interface CurriculumSubjectHistoryRepository extends JpaRepository<CurriculumSubjectHistory, Long> {
    List<CurriculumSubjectHistory> findByCurriculumSubjectIdOrderByCreatedAtDesc(Long curriculumSubjectId);
}
