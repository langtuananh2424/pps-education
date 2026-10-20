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

    /** Chấm lần 2 ESSAY (bổ sung 2026-10-20) — toàn bộ bản chấm (AI + giáo viên) của nhiều câu trả lời, mới nhất trước. */
    @Query("SELECT g FROM StudentAnswerGrading g JOIN FETCH g.grader WHERE g.studentAnswer.id IN :studentAnswerIds " +
            "ORDER BY g.gradedAt DESC, g.id DESC")
    List<StudentAnswerGrading> findHistoryByStudentAnswerIds(@Param("studentAnswerIds") Collection<Long> studentAnswerIds);
}
