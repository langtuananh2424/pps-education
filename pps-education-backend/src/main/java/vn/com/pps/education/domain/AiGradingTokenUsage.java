package vn.com.pps.education.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng ai_grading_token_usage (V192, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) —
 * 1 dòng cho MỖI lệnh gọi AI qua {@link vn.com.pps.education.service.NineRouterAiClient}, phục vụ trang
 * Quản trị hệ thống → Sử dụng token AI (xem
 * {@link vn.com.pps.education.service.AiGradingTokenUsageQueryService}).
 *
 * Mọi liên kết ngữ cảnh đều NULL được vì cùng 1 client phục vụ cả luồng Reflex (biết học sinh/bài/câu
 * hỏi) lẫn luồng chấm Writing UC-40/41. Dòng KHÔNG có ngữ cảnh vẫn phải được ghi — tổng trên trang cần
 * khớp hoá đơn nhà cung cấp, bỏ sót còn tệ hơn hiển thị "không rõ".
 */
@Getter
@Setter
@Entity
@Table(name = "ai_grading_token_usage")
public class AiGradingTokenUsage extends BaseAuditEntity {

    /**
     * Bước chấm theo NGHIỆP VỤ — khác {@link #operation} (tên kỹ thuật của lệnh gọi). 1 lần học sinh nộp
     * ghi âm sinh ra 2 dòng: TRANSCRIPTION (phiên âm mù) rồi SPEAKING (chấm nói), cả hai cùng
     * operation="chatWithAudioJson" nên không phân biệt được nếu chỉ dựa vào operation.
     */
    public enum Step { WRITING, TRANSCRIPTION, SPEAKING, CORRECTED_ANSWER, ESSAY, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_video_assignment_id")
    private ReviewVideoAssignment reviewVideoAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_video_question_id")
    private ReviewVideoQuestion reviewVideoQuestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 30)
    private Step step;

    @Column(name = "operation", nullable = false, length = 30)
    private String operation;

    @Column(name = "requested_model", length = 200)
    private String requestedModel;

    /** Model 9Router THỰC SỰ route tới — có thể khác requestedModel (tên combo), cần cho đối chiếu giá. */
    @Column(name = "served_model", length = 200)
    private String servedModel;

    @Column(name = "audio_attached", nullable = false)
    private boolean audioAttached;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "cached_tokens", nullable = false)
    private int cachedTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    /** Token thinking — tính tiền như output nhưng không nằm trong completionTokens ở 1 số provider. */
    @Column(name = "reasoning_tokens", nullable = false)
    private int reasoningTokens;

    @Column(name = "elapsed_ms", nullable = false)
    private long elapsedMs;

    /** false = kết quả bị loại (sai model/nội dung rỗng) nhưng token vẫn bị tính tiền. */
    @Column(name = "accepted", nullable = false)
    private boolean accepted = true;
}
