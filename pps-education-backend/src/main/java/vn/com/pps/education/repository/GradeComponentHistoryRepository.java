package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeComponentHistory;

public interface GradeComponentHistoryRepository extends JpaRepository<GradeComponentHistory, Long> {

    /** UC-19 (xoá thành phần điểm chưa có điểm nhập): FK grade_component_id NOT NULL không CASCADE — xoá history trước. */
    void deleteByGradeComponentId(Long gradeComponentId);
}
