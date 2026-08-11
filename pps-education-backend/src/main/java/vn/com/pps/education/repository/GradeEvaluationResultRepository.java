package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Bổ sung ngoài SDD gốc — StudentProfileService (FR-REP-04): JOIN FETCH lớp/kỳ học để tránh N+1 khi gộp toàn bộ điểm tổng kết của 1 học sinh qua mọi lớp/kỳ. */
    @Query("""
            SELECT r FROM GradeEvaluationResult r
            JOIN FETCH r.schoolClass sc
            JOIN FETCH r.academicTerm t
            WHERE r.student.id = :studentId
            ORDER BY t.startDate DESC, r.evaluationType ASC
            """)
    List<GradeEvaluationResult> findByStudentIdWithContext(@Param("studentId") Long studentId);
}
