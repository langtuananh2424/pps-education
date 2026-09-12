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

import java.time.OffsetDateTime;

/**
 * Bảng student_attitude_escalations — bổ sung ngoài SDD gốc, đã xác nhận
 * với người dùng 2026-09-12: cảnh báo thái độ học tập Yếu/Trung bình liên
 * tục 3 buổi phải được Quản lý điểm trường DUYỆT trước khi gửi xuống Phụ
 * huynh. Mirror 1:1 {@link HomeworkParentMeetingInvite} — KHÔNG dùng chung
 * {@code ApprovalFlow} (submittedBy NOT NULL — bắt buộc có người nộp)
 * vì bản ghi này do hệ thống TỰ SINH RA khi đếm streak chạm mốc 3, không
 * có "người nộp" thực sự. Xem StudentAttitudeEscalationService.
 */
@Getter
@Setter
@Entity
@Table(name = "student_attitude_escalations")
public class StudentAttitudeEscalation extends BaseAuditEntity {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(name = "streak_count", nullable = false)
    private int streakCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;
}
