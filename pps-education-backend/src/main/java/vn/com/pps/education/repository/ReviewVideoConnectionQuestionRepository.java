package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestion;

import java.util.List;

public interface ReviewVideoConnectionQuestionRepository extends JpaRepository<ReviewVideoConnectionQuestion, Long> {

    List<ReviewVideoConnectionQuestion> findByReviewVideoIdOrderByDisplayOrder(Long reviewVideoId);

    /** Gate bắt buộc khi Publish bộ CONNECTION (xem ReviewVideoService#updateSet). */
    boolean existsByReviewVideoId(Long reviewVideoId);
}
