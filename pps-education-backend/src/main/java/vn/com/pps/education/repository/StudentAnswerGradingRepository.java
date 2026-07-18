package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentAnswerGrading;

import java.util.Optional;

public interface StudentAnswerGradingRepository extends JpaRepository<StudentAnswerGrading, Long> {
    Optional<StudentAnswerGrading> findByStudentAnswerIdAndLatestIsTrue(Long studentAnswerId);
}
