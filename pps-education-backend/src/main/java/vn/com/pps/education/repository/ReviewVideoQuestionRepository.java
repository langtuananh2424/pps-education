package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoQuestion;

import java.util.List;

public interface ReviewVideoQuestionRepository extends JpaRepository<ReviewVideoQuestion, Long> {

    List<ReviewVideoQuestion> findByReviewVideoIdOrderByDisplayOrder(Long reviewVideoId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — dọn câu hỏi REFLEX khi "Xóa video" (xem ReviewVideoService#deleteVideo), sau khi đã chặn nếu còn bài nộp/tiến độ thật. */
    void deleteByReviewVideoId(Long reviewVideoId);
}
