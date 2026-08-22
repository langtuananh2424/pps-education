package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReflexQuestionProgress;

import java.util.List;
import java.util.Optional;

public interface ReflexQuestionProgressRepository extends JpaRepository<ReflexQuestionProgress, Long> {
    Optional<ReflexQuestionProgress> findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(
            Long reviewVideoQuestionId, Long studentId, Long reviewVideoAssignmentId);

    List<ReflexQuestionProgress> findByReviewVideoAssignmentIdAndStudentId(Long reviewVideoAssignmentId, Long studentId);
}
