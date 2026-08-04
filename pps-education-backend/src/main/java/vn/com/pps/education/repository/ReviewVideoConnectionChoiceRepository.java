package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionChoice;

import java.util.List;

public interface ReviewVideoConnectionChoiceRepository extends JpaRepository<ReviewVideoConnectionChoice, Long> {

    List<ReviewVideoConnectionChoice> findByReviewVideoConnectionQuestionIdOrderByDisplayOrder(Long reviewVideoConnectionQuestionId);
}
