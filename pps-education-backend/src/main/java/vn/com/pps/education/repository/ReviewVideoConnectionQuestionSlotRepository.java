package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestionSlot;

import java.util.List;

public interface ReviewVideoConnectionQuestionSlotRepository extends JpaRepository<ReviewVideoConnectionQuestionSlot, Long> {

    List<ReviewVideoConnectionQuestionSlot> findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(Long videoId, Long studentId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — dọn slot khi "Xóa video" (xem ReviewVideoService#deleteVideo), sau khi đã chặn nếu còn câu trả lời thật. */
    void deleteByReviewVideoConnectionQuestion_ReviewVideoId(Long videoId);
}
