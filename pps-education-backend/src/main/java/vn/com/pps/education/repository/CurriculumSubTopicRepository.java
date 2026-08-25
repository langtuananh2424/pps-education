package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumSubTopic;

import java.util.List;

public interface CurriculumSubTopicRepository extends JpaRepository<CurriculumSubTopic, Long> {
    List<CurriculumSubTopic> findByUnitIdOrderByDisplayOrder(Long unitId);
}
