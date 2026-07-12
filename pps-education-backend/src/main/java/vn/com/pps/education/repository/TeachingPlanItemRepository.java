package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.TeachingPlanItem;

import java.util.List;

public interface TeachingPlanItemRepository extends JpaRepository<TeachingPlanItem, Long> {
    List<TeachingPlanItem> findByTeachingPlanIdOrderByItemOrder(Long teachingPlanId);
}
