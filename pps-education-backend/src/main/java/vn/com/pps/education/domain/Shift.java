package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Bảng shifts (SDD > Nhân sự > Chấm công > a) — ca làm việc chuẩn (UC-09
 * Main Flow bước 3-4: xác định ngày làm việc + cửa sổ chấm công theo ca
 * cố định).
 */
@Getter
@Setter
@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime;

    @Column(name = "check_in_window_before_minutes", nullable = false)
    private int checkInWindowBeforeMinutes = 30;

    @Column(name = "check_in_window_after_minutes", nullable = false)
    private int checkInWindowAfterMinutes = 30;

    @Column(name = "check_out_window_before_minutes", nullable = false)
    private int checkOutWindowBeforeMinutes = 30;

    @Column(name = "check_out_window_after_minutes", nullable = false)
    private int checkOutWindowAfterMinutes = 60;

    /** CSV 1=T2...7=CN (VD "1,2,3,4,5,6"). */
    @Column(name = "applies_to_weekdays", nullable = false, length = 20)
    private String appliesToWeekdays = "1,2,3,4,5,6";

    public enum WeekParity { ALL, ODD, EVEN }

    @Enumerated(EnumType.STRING)
    @Column(name = "week_parity", nullable = false, length = 10)
    private WeekParity weekParity = WeekParity.ALL;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "import_job_id")
    private Long importJobId;
}
