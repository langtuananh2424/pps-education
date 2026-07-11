package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bảng employee_shifts (SDD > Nhân sự > Chấm công > b) — gán ca cho nhân
 * sự. Ràng buộc: mỗi nhân sự 1 thời điểm chỉ 1 ca đang active
 * (effective_to NULL) — unique partial index ở V7.
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
