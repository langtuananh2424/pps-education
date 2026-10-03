package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.lms.domain.StudentAnswer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {
    /** UC-40 SDD: câu hỏi đã có student_answers thì cấm sửa content/đáp án đúng. */
    boolean existsByQuestionId(Long questionId);

    List<StudentAnswer> findByExerciseAttemptId(Long exerciseAttemptId);

    List<StudentAnswer> findByExerciseAttemptIdIn(List<Long> exerciseAttemptIds);

    Optional<StudentAnswer> findByExerciseAttemptIdAndQuestionId(Long exerciseAttemptId, Long questionId);

    /** UC-41 Main Flow bước 1: câu tự luận/Nói đã nộp, chưa có bản chấm hiện hành. */
    @Query("""
            SELECT sa FROM StudentAnswer sa
            WHERE sa.autoGradable = false
            AND sa.exerciseAttempt.submittedAt IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM StudentAnswerGrading g WHERE g.studentAnswer = sa AND g.latest = true
            )
            ORDER BY sa.exerciseAttempt.submittedAt
            """)
    List<StudentAnswer> findPendingManualGrading();

    /** UC-74 (bổ sung 2026-09-30) — câu trả lời kèm câu hỏi (dạng câu, độ khó) của nhiều lượt làm, 1 truy vấn. */
    @Query("SELECT sa FROM StudentAnswer sa JOIN FETCH sa.question WHERE sa.exerciseAttempt.id IN :attemptIds")
    List<StudentAnswer> findWithQuestionByExerciseAttemptIdIn(@Param("attemptIds") Collection<Long> attemptIds);
}
