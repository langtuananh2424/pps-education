package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bảng work_calendar (SDD > Nhân sự > Chấm công > c) — lịch làm việc
 * override theo ngày cụ thể (UC-09 Main Flow bước 3: đối chiếu trước pattern
 * mặc định của shift). Chỉ chứa các ngày ngoại lệ.
 */
@Getter
@Setter
@Entity
@Table(name = "work_calendar")
public class WorkCalendar {

    public enum DayType { WORKING, OFF, HOLIDAY, COMPENSATORY }

    public enum Scope { ALL, SHIFT, EMPLOYEE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_date", nullable = false)
    private LocalDate calendarDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 20)
    private DayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to_scope", nullable = false, length = 20)
    private Scope appliesToScope = Scope.ALL;

    /** Chỉ set khi scope=SHIFT. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    /** Chỉ set khi scope=EMPLOYEE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(length = 500)
    private String description;

    @Column(name = "import_job_id")
    private Long importJobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
