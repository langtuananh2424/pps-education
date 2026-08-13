package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestion;

import java.util.List;

public interface ReviewVideoConnectionQuestionRepository extends JpaRepository<ReviewVideoConnectionQuestion, Long> {

    List<ReviewVideoConnectionQuestion> findByReviewVideoIdOrderByDisplayOrder(Long reviewVideoId);

    /** Gate bắt buộc khi Publish bộ CONNECTION (xem ReviewVideoService#updateSet). */
    boolean existsByReviewVideoId(Long reviewVideoId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — toàn bộ câu hỏi của 1 nhóm video (1 bộ CONNECTION có thể nhiều video), dùng cho trang "Xem chi tiết" BTVN (ReviewVideoReportService). */
    List<ReviewVideoConnectionQuestion> findByReviewVideoIdIn(List<Long> videoIds);
}
