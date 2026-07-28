package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoQuestion;

import java.util.List;

public interface ReviewVideoQuestionRepository extends JpaRepository<ReviewVideoQuestion, Long> {

    List<ReviewVideoQuestion> findByReviewVideoIdOrderByDisplayOrder(Long reviewVideoId);
}
