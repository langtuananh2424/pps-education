package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bảng students (SDD > Học sinh & Phụ huynh > a). Mở rộng 1-1 từ users cho
 * học sinh (UC-13).
 */
@Getter
@Setter
@Entity
@Table(name = "students")
public class Student extends BaseAuditEntity {

    public enum Gender { MALE, FEMALE, OTHER }

    /**
     * DEFERRAL bổ sung ngoài 5 giá trị gốc của SDD cho "Bảo lưu" (UC-14) —
     * xem docs/sdd-groups/04-hoc-sinh-and-phu-huynh.md.
     */
    public enum Status { ACTIVE, SUSPENDED, EXPELLED, GRADUATED, WITHDRAWN, DEFERRAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "student_code", nullable = false, unique = true, length = 20)
    private String studentCode;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "portrait_url", length = 500)
    private String portraitUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_site_id")
    private Site primarySite;

    @Column(name = "original_school", length = 200)
    private String originalSchool;

    @Column(name = "original_class", length = 50)
    private String originalClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "enrollment_date", nullable = false)
    private LocalDate enrollmentDate;

    @Column(name = "graduation_date")
    private LocalDate graduationDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
