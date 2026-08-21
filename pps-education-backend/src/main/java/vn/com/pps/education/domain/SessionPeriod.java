package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Bảng session_periods (SDD > Học thuật > Lịch dạy & Điểm danh > b) —
 * tiết học trong 1 buổi. Tự động sinh khi tạo class_sessions (xem
 * ClassSessionService), GV có thể sửa chi tiết sau.
 */
@Getter
@Setter
@Entity
@Table(name = "session_periods")
public class SessionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSession classSession;

    /** Buổi trong ngày lúc tạo (copy từ SitePeriodTemplate.dayPart — V129, bổ sung ngoài SDD gốc, xác nhận 2026-08-20). */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_part", nullable = false, length = 20)
    private SitePeriodTemplate.DayPart dayPart;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** NULL = dùng primary_teacher của session (SDD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    @Column(name = "content_note", columnDefinition = "TEXT")
    private String contentNote;
}
