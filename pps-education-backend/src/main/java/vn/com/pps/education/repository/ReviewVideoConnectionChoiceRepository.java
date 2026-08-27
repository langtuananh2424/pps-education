package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionChoice;

import java.util.List;

public interface ReviewVideoConnectionChoiceRepository extends JpaRepository<ReviewVideoConnectionChoice, Long> {

    List<ReviewVideoConnectionChoice> findByReviewVideoConnectionQuestionIdOrderByDisplayOrder(Long reviewVideoConnectionQuestionId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — dọn đáp án khi "Xóa video" (xem ReviewVideoService#deleteVideo). */
    void deleteByReviewVideoConnectionQuestionIdIn(List<Long> reviewVideoConnectionQuestionIds);
}
