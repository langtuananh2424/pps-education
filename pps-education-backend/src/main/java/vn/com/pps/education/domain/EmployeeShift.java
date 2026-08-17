package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bảng employee_shifts (SDD > Nhân sự > Chấm công > b) — gán ca cho nhân
 * sự. V124 (2026-08-14): 1 nhân sự có thể có NHIỀU ca active song song
 * (effective_to NULL) — bỏ ràng buộc "1 ca active" gốc ở V7, thay bằng
 * validate chống chồng chéo lịch ở EmployeeShiftService.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_shifts")
public class EmployeeShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL = đang áp dụng. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "import_job_id")
    private Long importJobId;
}
