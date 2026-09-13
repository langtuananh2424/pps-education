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
 * Bảng homework_parent_meeting_invites — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-09-12: "Thư mời phụ huynh tới làm việc"
 * (Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE, khi
 * học sinh thiếu bài liên tục 4 buổi) phải được Quản lý điểm trường DUYỆT
 * trước khi gửi xuống Phụ huynh.
 *
 * KHÔNG dùng chung {@link ApprovalFlow} (submittedBy NOT NULL — bắt buộc
 * có người nộp) vì bản ghi này do hệ thống TỰ SINH RA khi đếm streak chạm
 * mốc 4, không có "người nộp" thực sự — dùng status riêng của entity này.
 * Xem HomeworkParentMeetingInviteService.
 */
@Getter
@Setter
@Entity
@Table(name = "homework_parent_meeting_invites")
public class HomeworkParentMeetingInvite extends BaseAuditEntity {

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

    @Column(name = "channel_label", nullable = false, length = 50)
    private String channelLabel;

    @Column(name = "miss_count", nullable = false)
    private int missCount;

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
