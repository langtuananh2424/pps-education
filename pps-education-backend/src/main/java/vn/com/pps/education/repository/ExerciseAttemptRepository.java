package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ExerciseAttempt;

import java.util.List;

public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, Long> {
    long countByExerciseIdAndStudentId(Long exerciseId, Long studentId);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — tất cả lượt làm BTVN đã GIAO
     * (exercise_assignment_id khác NULL, loại trừ SELF_PRACTICE) của 1 học sinh, mọi lớp/exercise,
     * dùng cho tab "Bài tập về nhà" của StudentProfileService (mirror pattern findByStudentIdWithContext
     * của các repo nguồn khác trong hồ sơ học tập).
     */
    @Query("select a from ExerciseAttempt a " +
            "join fetch a.exercise ex " +
            "join fetch a.exerciseAssignment ea " +
            "join fetch ea.schoolClass " +
            "where a.student.id = :studentId and a.exerciseAssignment is not null " +
            "order by a.submittedAt desc nulls last, a.startedAt desc")
    List<ExerciseAttempt> findByStudentIdWithContext(@Param("studentId") Long studentId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — đếm lượt làm CHỈ trong phạm vi 1 bản giao cụ thể (mirror ReviewVideoQuestionSubmissionRepository#countBy...AndReviewVideoAssignmentId), dùng cho retake/maxAttempts khi giao lại = 1 lượt mới. */
    long countByExerciseAssignmentIdAndStudentId(Long exerciseAssignmentId, Long studentId);

    List<ExerciseAttempt> findByExerciseIdAndStudentIdOrderByAttemptNumberDesc(Long exerciseId, Long studentId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — lượt làm CHỈ trong phạm vi 1 bản giao cụ thể (dùng cho listMyAssignedExercises#toAssignedResponse, tránh lộ điểm/trạng thái của lượt giao khác). */
    List<ExerciseAttempt> findByExerciseAssignmentIdAndStudentIdOrderByAttemptNumberDesc(Long exerciseAssignmentId, Long studentId);

    List<ExerciseAttempt> findByExerciseAssignmentIdOrderByAttemptNumberDesc(Long exerciseAssignmentId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — dùng cho ExerciseReportService#getQuestionStats (phân tích câu hỏi theo LƯỢT ĐẦU TIÊN, không phải lượt mới nhất). */
    List<ExerciseAttempt> findByExerciseAssignmentIdOrderByAttemptNumberAsc(Long exerciseAssignmentId);
}
