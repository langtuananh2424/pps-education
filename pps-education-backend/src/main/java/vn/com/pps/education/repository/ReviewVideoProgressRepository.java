package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoProgress;

import java.util.List;
import java.util.Optional;

public interface ReviewVideoProgressRepository extends JpaRepository<ReviewVideoProgress, Long> {

    Optional<ReviewVideoProgress> findByReviewVideoIdAndStudentId(Long reviewVideoId, Long studentId);

    /** UC-23a: thống kê giáo viên — lấy 1 lần toàn bộ tiến độ của mọi video trong 1 bộ, ghép ma trận ở Service. */
    List<ReviewVideoProgress> findByReviewVideoIdIn(List<Long> reviewVideoIds);
}
