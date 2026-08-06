package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeEvaluationComponentHistory;

public interface GradeEvaluationComponentHistoryRepository extends JpaRepository<GradeEvaluationComponentHistory, Long> {

    /** UC-19 (xoá thành phần điểm chưa có điểm nhập): FK grade_evaluation_component_id NOT NULL không CASCADE — xoá history trước. */
    void deleteByGradeEvaluationComponentId(Long gradeEvaluationComponentId);
}
