package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng payroll_entries (SDD > Nhân sự > Bảng lương > b) — chi tiết lương 1
 * nhân sự trong 1 kỳ (UC-12, chỉ đọc trong phạm vi PR này — xem V9).
 */
@Getter
@Setter
@Entity
@Table(name = "payroll_entries")
public class PayrollEntry {

    public enum Status { CALCULATED, APPROVED, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod payrollPeriod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Snapshot từ hợp đồng ACTIVE tại thời điểm chốt. */
    @Column(name = "base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;

    /** Cho GV. */
    @Column(name = "teaching_hours", precision = 10, scale = 2)
    private BigDecimal teachingHours;

    @Column(name = "hourly_rate", precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    /** Từ attendance_records. */
    @Column(name = "work_days", precision = 5, scale = 2)
    private BigDecimal workDays = BigDecimal.ZERO;

    /** Từ commendations. */
    @Column(precision = 15, scale = 2)
    private BigDecimal bonuses = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal penalties = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "social_insurance", precision = 15, scale = 2)
    private BigDecimal socialInsurance = BigDecimal.ZERO;

    @Column(name = "health_insurance", precision = 15, scale = 2)
    private BigDecimal healthInsurance = BigDecimal.ZERO;

    @Column(name = "unemployment_insurance", precision = 15, scale = 2)
    private BigDecimal unemploymentInsurance = BigDecimal.ZERO;

    @Column(name = "gross_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "total_deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal netSalary;

    /** Breakdown công thức tính. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_details", columnDefinition = "jsonb")
    private Map<String, Object> calculationDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CALCULATED;
}
