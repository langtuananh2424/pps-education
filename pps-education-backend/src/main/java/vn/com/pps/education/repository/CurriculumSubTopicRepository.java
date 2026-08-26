package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumSubTopic;

import java.util.List;

public interface CurriculumSubTopicRepository extends JpaRepository<CurriculumSubTopic, Long> {
    List<CurriculumSubTopic> findByUnitIdOrderByDisplayOrder(Long unitId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa Unit" (xem CurriculumService#deleteUnit): còn Sub Topic thì không cho xóa. */
    boolean existsByUnitId(Long unitId);
}
