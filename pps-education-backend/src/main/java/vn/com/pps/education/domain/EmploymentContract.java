package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng employment_contracts (SDD > Nhân sự > Hồ sơ nhân sự > b) — lịch sử
 * hợp đồng lao động của 1 nhân sự (UC-08 Main Flow bước 3). Mỗi nhân sự chỉ
 * 1 hợp đồng ACTIVE tại 1 thời điểm (unique partial index ở V3).
 */
@Getter
@Setter
@Entity
@Table(name = "employment_contracts")
public class EmploymentContract extends BaseAuditEntity {

    public enum ContractType { PROBATION, FIXED_TERM, INDEFINITE, SEASONAL }

    public enum SalaryType { MONTHLY, HOURLY }

    public enum Status { DRAFT, ACTIVE, EXPIRED, TERMINATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "contract_number", nullable = false, unique = true, length = 100)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 30)
    private ContractType contractType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** NULL nếu INDEFINITE. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", nullable = false, length = 20)
    private SalaryType salaryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
