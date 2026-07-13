package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bảng partner_feedbacks_history (SDD "Có partner_feedbacks_history",
 * không định nghĩa cột), migration V27. UC-39 A1: action=EXCHANGE lưu
 * từng lượt trao đổi qua lại giữa Quản lý điểm trường và Đại diện trường
 * liên kết trước khi chuyển RESOLVED — không chỉ CREATED/UPDATED như các
 * bảng history khác.
 */
@Getter
@Setter
@Entity
@Table(name = "partner_feedbacks_history")
public class PartnerFeedbackHistory {

    public enum Action { CREATED, UPDATED, EXCHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private PartnerFeedback feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
