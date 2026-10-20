package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.StudentAnswerGrading;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentAnswerGradingRepository extends JpaRepository<StudentAnswerGrading, Long> {
    Optional<StudentAnswerGrading> findByStudentAnswerIdAndLatestIsTrue(Long studentAnswerId);

    /** UC-74 (bổ sung 2026-09-30) — bản chấm hiện hành (kèm điểm tiêu chí) của nhiều câu trả lời, 1 truy vấn. */
    List<StudentAnswerGrading> findByStudentAnswerIdInAndLatestIsTrue(Collection<Long> studentAnswerIds);
}
