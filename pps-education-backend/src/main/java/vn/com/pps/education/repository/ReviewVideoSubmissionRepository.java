package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoSubmission;

import java.util.List;
import java.util.Optional;

public interface ReviewVideoSubmissionRepository extends JpaRepository<ReviewVideoSubmission, Long> {

    Optional<ReviewVideoSubmission> findByReviewVideoIdAndStudentId(Long reviewVideoId, Long studentId);

    /** UC-23b: Giáo viên xem danh sách bài đã nộp — lọc theo video của 1 bộ + roster ACTIVE của 1 lớp. */
    List<ReviewVideoSubmission> findByReviewVideoIdInAndStudentIdIn(List<Long> reviewVideoIds, List<Long> studentIds);
}
