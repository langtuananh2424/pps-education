package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng class_session_check_ins (UC-71 "Nhận lớp", bổ sung ngoài SDD/SRS
 * gốc, đã xác nhận với người dùng — migration V126) — 1 record = 1 giáo
 * viên đã có mặt để dạy 1 buổi học cụ thể ({@link #classSession}, unique).
 * Không có check-out; buổi học đã kết thúc mà không có record ở đây được
 * coi là "vắng/không nhận lớp" (tính khi đọc, không lưu trạng thái này).
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Getter
@Setter
@Entity
@Table(name = "class_session_check_ins")
public class ClassSessionCheckIn extends BaseAuditEntity {

    public enum Status { ON_TIME, LATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_session_id", nullable = false, unique = true)
    private ClassSession classSession;

    /** Phải khớp classSession.primaryTeacher tại thời điểm nhận lớp — validate ở Service. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "check_in_time", nullable = false)
    private OffsetDateTime checkInTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** Điểm trường xác định qua classSession.schoolClass.site — lưu lại để audit/query, không phải nguồn chân lý. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;
}
