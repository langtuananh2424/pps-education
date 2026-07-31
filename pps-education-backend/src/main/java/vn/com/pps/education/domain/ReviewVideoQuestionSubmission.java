package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng review_video_question_submissions (SDD > LMS & Portal > Kho
 * Video Ôn tập) — thay thế ReviewVideoSubmission (V53) từ V57, 2026-07-28
 * (bổ sung ngoài SDD gốc đã xác nhận với người dùng): Học sinh nộp audio
 * trả lời cho 1 câu hỏi (ReviewVideoQuestion), GIỮ LỊCH SỬ mỗi lần nộp
 * (attemptNumber tăng dần, UNIQUE review_video_question_id+student_id+
 * review_video_assignment_id+attempt_number — khác hẳn cơ chế ghi đè cũ).
 * Giáo viên chấm điểm/nhận xét cho TỪNG attempt riêng (không xoá điểm
 * attempt trước khi nộp attempt mới, khác cơ chế cũ).
 *
 * V69 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31, fix
 * bug "Đã nộp bài" hiện sai khi giao lại): thêm liên kết tới
 * {@link ReviewVideoAssignment} — mỗi attempt gắn với ĐÚNG 1 lần giao đã
 * cấp quyền nộp (không còn tính lịch sử xuyên suốt mọi lần giao). NULL cho
 * dữ liệu trước V69 (không xác định được lần giao nào), coi như không
 * thuộc lần giao hiện tại nào — xem Javadoc ReviewVideoService.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_question_submissions")
public class ReviewVideoQuestionSubmission extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_question_id", nullable = false)
    private ReviewVideoQuestion reviewVideoQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** V69 — NULL cho dữ liệu trước V69 (xem Javadoc lớp). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_video_assignment_id")
    private ReviewVideoAssignment reviewVideoAssignment;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "audio_url", nullable = false, length = 1000)
    private String audioUrl;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    /** NULL = chưa chấm. */
    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;
}
