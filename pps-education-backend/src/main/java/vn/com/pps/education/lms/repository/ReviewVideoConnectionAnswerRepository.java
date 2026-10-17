package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.ReviewVideoConnectionAnswer;

import java.util.List;

public interface ReviewVideoConnectionAnswerRepository extends JpaRepository<ReviewVideoConnectionAnswer, Long> {

    List<ReviewVideoConnectionAnswer> findByWatchSessionId(Long watchSessionId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — batch nhiều watchSessionId cùng lúc (tránh N+1), dùng dựng popup "Hoàn thành"/"Kết quả" (xem ReviewVideoService#getConnectionAnswerHistory). */
    List<ReviewVideoConnectionAnswer> findByWatchSession_IdIn(List<Long> watchSessionIds);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — toàn bộ câu trả lời của 1 học sinh cho 1 video, MỌI lượt/chu kỳ (dùng tính điểm pass tổng, lấy bản mới nhất mỗi câu hỏi ở tầng Service). */
    /** Chỉ câu trả lời của các lượt ĐANG TÍNH (chưa lưu trữ do "Làm lại từ đầu", 2026-10-17) — lịch sử cũ vẫn còn trong bảng nhưng không tính điểm. */
    List<ReviewVideoConnectionAnswer> findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentIdAndWatchSession_ArchivedAtIsNull(Long videoId, Long studentId);

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — toàn bộ câu trả lời (mọi học sinh, mọi lượt) cho 1 nhóm video, dùng cho trang "Xem chi tiết" BTVN CONNECTION (ReviewVideoReportService) — 1 query bulk thay vì lặp theo từng học sinh. */
    /** Như trên: bỏ câu trả lời của lượt đã lưu trữ. */
    List<ReviewVideoConnectionAnswer> findByReviewVideoConnectionQuestion_ReviewVideoIdInAndWatchSession_ArchivedAtIsNull(List<Long> videoIds);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): câu hỏi CONNECTION của video đã có học sinh trả lời thì không cho xóa. */
    boolean existsByReviewVideoConnectionQuestion_ReviewVideoId(Long reviewVideoId);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-24 — gate "Xóa câu hỏi" CONNECTION (xem ReviewVideoService#deleteConnectionQuestion). */
    boolean existsByReviewVideoConnectionQuestionId(Long reviewVideoConnectionQuestionId);
}
