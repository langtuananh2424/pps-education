package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bảng student_answer_grading (SDD > LMS & Portal > Ngân hàng câu hỏi &
 * Bài tập > i) — GV chấm tự luận/Nói (UC-41). Không có history — sửa
 * điểm tạo bản ghi mới (latest=true, cột DB is_final), bản cũ chuyển
 * latest=false thay vì UPDATE trực tiếp (SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "student_answer_grading")
public class StudentAnswerGrading {

    /**
     * V138, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — HUMAN (GV chấm tay, UC-41)
     * hay AI (chấm tự động ESSAY thuộc Bài Exercise.skillCategory=WRITING, xem WritingAiGradingService/
     * ExerciseAttemptService#gradeAndFinalize). grader vẫn LUÔN là 1 User thật (dùng
     * exercise.createdBy khi gradingSource=AI, không thêm user hệ thống ảo) — field này chỉ để phân
     * biệt rõ trong dữ liệu/lịch sử, tránh hiểu nhầm giáo viên đã tự tay chấm.
     */
    public enum GradingSource { HUMAN, AI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_answer_id", nullable = false)
    private StudentAnswer studentAnswer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grader_user_id", nullable = false)
    private User grader;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at", nullable = false)
    private OffsetDateTime gradedAt = OffsetDateTime.now();

    @Column(name = "is_final", nullable = false)
    private boolean latest = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_source", nullable = false, length = 10)
    private GradingSource gradingSource = GradingSource.HUMAN;
}
