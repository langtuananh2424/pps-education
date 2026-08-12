package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestionSlot;

import java.util.List;

public interface ReviewVideoConnectionQuestionSlotRepository extends JpaRepository<ReviewVideoConnectionQuestionSlot, Long> {

    List<ReviewVideoConnectionQuestionSlot> findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(Long videoId, Long studentId);
}
