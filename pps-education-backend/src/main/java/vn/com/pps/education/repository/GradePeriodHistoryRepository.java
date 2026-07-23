package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodHistory;

public interface GradePeriodHistoryRepository extends JpaRepository<GradePeriodHistory, Long> {

    /** UC-19 (xoá kỳ đánh giá rỗng): FK grade_period_id NOT NULL không CASCADE — xoá history trước. */
    void deleteByGradePeriodId(Long gradePeriodId);
}
