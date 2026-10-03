package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.GradeComponentSetupHistory;

public interface GradeComponentSetupHistoryRepository extends JpaRepository<GradeComponentSetupHistory, Long> {

    /** UC-19 (xoá setup rỗng): FK grade_component_setup_id NOT NULL không CASCADE — xoá history trước. */
    void deleteByGradeComponentSetupId(Long gradeComponentSetupId);
}
