package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng student_attitude_alert_state (migration V171, bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng 2026-09-12) — đếm streak thái độ học
 * tập Yếu/Trung bình liên tục của 1 học sinh trong 1 lớp. Mirror
 * {@link StudentHomeworkAlertState} nhưng bỏ khái niệm "channel" vì thái
 * độ chỉ có đúng 1 luồng cảnh báo (không tách 2 kênh như BTVN). Xem
 * {@code StudentAttitudeAlertTrackingService} để biết đầy đủ business logic.
 */
@Getter
@Setter
@Entity
@Table(name = "student_attitude_alert_state")
public class StudentAttitudeAlertState extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    /** NULL = chưa xác định được kỳ hiện tại (điểm trường chưa cấu hình academic_terms cho khoảng ngày này). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id")
    private AcademicTerm academicTerm;

    @Column(name = "consecutive_low_count", nullable = false)
    private int consecutiveLowCount = 0;
}
