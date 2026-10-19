package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.lms.domain.StudentAnswerGrading;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentAnswerGradingRepository extends JpaRepository<StudentAnswerGrading, Long> {
    Optional<StudentAnswerGrading> findByStudentAnswerIdAndLatestIsTrue(Long studentAnswerId);

    /** UC-74 (bổ sung 2026-09-30) — bản chấm hiện hành (kèm điểm tiêu chí) của nhiều câu trả lời, 1 truy vấn. */
    List<StudentAnswerGrading> findByStudentAnswerIdInAndLatestIsTrue(Collection<Long> studentAnswerIds);

    /**
     * Chấm lần 2 (bổ sung ngoài SDD gốc, xác nhận 2026-10-19) — mọi bản chấm của AI (kể cả bản đã bị GV chấm đè)
     * cho bài tập giao cho 1 lớp, mới nhất trước. Lượt làm không gắn bản giao (tự luyện) bị loại.
     */
    @Query("""
            SELECT g FROM StudentAnswerGrading g
            WHERE g.gradingSource = :source
            AND g.studentAnswer.exerciseAttempt.exerciseAssignment.schoolClass.id = :classId
            ORDER BY g.gradedAt DESC
            """)
    List<StudentAnswerGrading> findByGradingSourceAndClassId(@Param("source") StudentAnswerGrading.GradingSource source,
                                                              @Param("classId") Long classId);
}
