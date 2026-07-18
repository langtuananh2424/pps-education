package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng attendance_marks (SDD > Học thuật > Lịch dạy & Điểm danh > d) —
 * điểm danh cấp buổi (mỗi học sinh), nguồn dữ liệu chính để tính chuyên
 * cần (UC-15).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_marks")
public class AttendanceMark {

    public enum Status { PRESENT, ABSENT, EXCUSED, LATE, EARLY_LEAVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_session_id", nullable = false)
    private AttendanceSession attendanceSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "check_in_time")
    private OffsetDateTime checkInTime;

    @Column(name = "minutes_late")
    private Integer minutesLate;

    @Column(name = "minutes_early_leave")
    private Integer minutesEarlyLeave;

    @Column(name = "absence_reason", columnDefinition = "TEXT")
    private String absenceReason;

    /** Thời điểm đã gửi thông báo cho PH (SDD). */
    @Column(name = "notified_parent_at")
    private OffsetDateTime notifiedParentAt;
}
