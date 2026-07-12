package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng attendance_period_marks (SDD > Học thuật > Lịch dạy & Điểm danh >
 * e) — điểm danh chi tiết theo tiết (UC-15 Main Flow bước 3). Không có
 * history riêng — "thay đổi ghi vào attendance_marks_history" (SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_period_marks")
public class AttendancePeriodMark {

    public enum Status { PRESENT, ABSENT, EXCUSED, LATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_mark_id", nullable = false)
    private AttendanceMark attendanceMark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_period_id", nullable = false)
    private SessionPeriod sessionPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String note;
}
