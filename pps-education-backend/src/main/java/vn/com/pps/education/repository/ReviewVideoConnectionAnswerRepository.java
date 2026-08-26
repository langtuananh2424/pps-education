package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoConnectionAnswer;

import java.util.List;

public interface ReviewVideoConnectionAnswerRepository extends JpaRepository<ReviewVideoConnectionAnswer, Long> {

    List<ReviewVideoConnectionAnswer> findByWatchSessionId(Long watchSessionId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — toàn bộ câu trả lời của 1 học sinh cho 1 video, MỌI lượt/chu kỳ (dùng tính điểm pass tổng, lấy bản mới nhất mỗi câu hỏi ở tầng Service). */
    List<ReviewVideoConnectionAnswer> findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(Long videoId, Long studentId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — toàn bộ câu trả lời (mọi học sinh, mọi lượt) cho 1 nhóm video, dùng cho trang "Xem chi tiết" BTVN CONNECTION (ReviewVideoReportService) — 1 query bulk thay vì lặp theo từng học sinh. */
    List<ReviewVideoConnectionAnswer> findByReviewVideoConnectionQuestion_ReviewVideoIdIn(List<Long> videoIds);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): câu hỏi CONNECTION của video đã có học sinh trả lời thì không cho xóa. */
    boolean existsByReviewVideoConnectionQuestion_ReviewVideoId(Long reviewVideoId);
}
