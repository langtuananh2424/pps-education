package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bảng employees (SDD > Nhân sự > Hồ sơ nhân sự > a). Mở rộng 1-1 từ users
 * cho nhân sự (UC-08).
 */
@Getter
@Setter
@Entity
@Table(name = "employees")
public class Employee extends BaseAuditEntity {

    public enum EmployeeType { TEACHER, STAFF, MANAGER }

    public enum Status { ACTIVE, ON_LEAVE, TERMINATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "id_card_number", unique = true, length = 20)
    private String idCardNumber;

    @Column(name = "id_card_issued_date")
    private LocalDate idCardIssuedDate;

    @Column(name = "id_card_issued_place", length = 200)
    private String idCardIssuedPlace;

    @Column(name = "permanent_address", length = 500)
    private String permanentAddress;

    @Column(name = "current_address", length = 500)
    private String currentAddress;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Column(name = "social_insurance_number", length = 20)
    private String socialInsuranceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_type", nullable = false, length = 20)
    private EmployeeType employeeType;

    @Column(name = "position_title", length = 200)
    private String positionTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Miễn trừ chấm công (FR-HRM-02) / miễn trừ hoặc quy trình duyệt đơn riêng (FR-HRM-03). */
    @Column(name = "is_management", nullable = false)
    private boolean management = false;

    @Column(name = "is_default_shift_required", nullable = false)
    private boolean defaultShiftRequired = true;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
