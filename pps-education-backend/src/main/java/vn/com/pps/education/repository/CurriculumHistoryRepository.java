package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumHistory;

import java.util.List;

public interface CurriculumHistoryRepository extends JpaRepository<CurriculumHistory, Long> {
    List<CurriculumHistory> findByCurriculumIdOrderByCreatedAtDesc(Long curriculumId);
}
