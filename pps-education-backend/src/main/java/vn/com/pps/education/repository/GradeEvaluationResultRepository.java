package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeComponentSetup;
import vn.com.pps.education.domain.GradeEvaluationResult;

import java.util.List;
import java.util.Optional;

public interface GradeEvaluationResultRepository extends JpaRepository<GradeEvaluationResult, Long> {

    Optional<GradeEvaluationResult> findBySchoolClassIdAndStudentIdAndAcademicTermIdAndEvaluationType(
            Long classId, Long studentId, Long academicTermId, GradeComponentSetup.EvaluationType evaluationType);

    List<GradeEvaluationResult> findBySchoolClassIdAndAcademicTermIdAndEvaluationTypeOrderByStudentId(
            Long classId, Long academicTermId, GradeComponentSetup.EvaluationType evaluationType);

    /** UC-19 (xoá setup rỗng): chặn xoá setup còn điểm tổng kết. */
    long countBySchoolClassIdAndAcademicTermIdAndEvaluationType(
            Long classId, Long academicTermId, GradeComponentSetup.EvaluationType evaluationType);
}
