package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng student_homework_alert_state (migration V92, bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-08-06) — đếm streak/tổng số lần thiếu
 * BTVN của 1 học sinh trong 1 lớp, TÁCH RIÊNG theo từng kênh (Ngữ pháp/
 * Video — 2 chuỗi giao bài độc lập). Xem
 * {@code HomeworkAlertTrackingService} để biết đầy đủ business logic.
 */
@Getter
@Setter
@Entity
@Table(name = "student_homework_alert_state")
public class StudentHomeworkAlertState extends BaseAuditEntity {

    public enum Channel { GRAMMAR, VIDEO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    /** NULL = chưa xác định được kỳ hiện tại (điểm trường chưa cấu hình academic_terms cho khoảng ngày này). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id")
    private AcademicTerm academicTerm;

    @Column(name = "consecutive_miss_count", nullable = false)
    private int consecutiveMissCount = 0;

    @Column(name = "total_miss_count_in_term", nullable = false)
    private int totalMissCountInTerm = 0;

    @Column(name = "type2_alert_sent", nullable = false)
    private boolean type2AlertSent = false;
}
