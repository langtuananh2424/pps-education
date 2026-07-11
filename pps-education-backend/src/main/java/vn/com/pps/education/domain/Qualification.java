package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bảng qualifications (SDD > Nhân sự > Hồ sơ nhân sự > c) — bằng cấp/chứng
 * chỉ sư phạm của 1 nhân sự (UC-08 Main Flow bước 2). Không có lịch sử
 * (bảng phụ, ít thay đổi — theo SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "qualifications")
public class Qualification {

    public enum QualificationType { DEGREE, PEDAGOGY_CERT, LANGUAGE_CERT, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_type", nullable = false, length = 30)
    private QualificationType qualificationType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String issuer;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "file_url", length = 500)
    private String fileUrl;
}
