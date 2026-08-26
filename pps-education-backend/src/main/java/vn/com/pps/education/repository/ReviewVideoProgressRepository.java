package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoProgress;

import java.util.List;
import java.util.Optional;

public interface ReviewVideoProgressRepository extends JpaRepository<ReviewVideoProgress, Long> {

    /**
     * V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thay
     * {@code findByReviewVideoIdAndStudentId} (rollup CHUNG mọi lần giao, đã bỏ): 1 rollup viewCount/
     * completed riêng cho ĐÚNG 1 lần giao (bản giao), mirror cách review_video_question_submissions
     * (REFLEX) đã scope theo assignment từ V69.
     */
    Optional<ReviewVideoProgress> findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentId(
            Long reviewVideoId, Long studentId, Long reviewVideoAssignmentId);

    /**
     * UC-23a: thống kê giáo viên — lấy 1 lần toàn bộ tiến độ của mọi video trong 1 bộ, ghép ma trận ở
     * Service. V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thêm lọc theo
     * đúng lần giao đang xem báo cáo (ReviewVideoReportService#getStudentStats scope theo 1
     * assignmentId cụ thể) — không lọc sẽ trộn tiến độ của các lần giao KHÁC cùng bộ+lớp vào chung 1
     * báo cáo.
     */
    List<ReviewVideoProgress> findByReviewVideoIdInAndReviewVideoAssignmentId(List<Long> reviewVideoIds, Long reviewVideoAssignmentId);

    /** Mirror method cũ (chưa lọc assignment) — dùng cho 1-2 màn tổng quan gộp cả bộ chưa tách theo từng lần giao riêng (xem ghi chú tại nơi gọi). */
    List<ReviewVideoProgress> findByReviewVideoIdIn(List<Long> reviewVideoIds);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa video" (xem ReviewVideoService#deleteVideo): đã có tiến độ xem thì không cho xóa. */
    boolean existsByReviewVideoId(Long reviewVideoId);
}
