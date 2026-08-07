package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bảng class_enrollments (SDD > Học thuật > Khung chương trình & Lớp học
 * > e) — học sinh trong lớp. Bảng nền tảng cho UC-42 (chọn lớp đang xem,
 * Phân hệ 7). importJobId (import Excel lớp liên kết, FR-CRM-04) chưa có
 * ImportJob entity — map thô kiểu Long, không @ManyToOne.
 */
@Getter
@Setter
@Entity
@Table(name = "class_enrollments")
public class ClassEnrollment {

    public enum Status { ACTIVE, WITHDRAWN, TRANSFERRED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "enrolled_date", nullable = false)
    private LocalDate enrolledDate;

    @Column(name = "withdrawn_date")
    private LocalDate withdrawnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "withdraw_reason", columnDefinition = "TEXT")
    private String withdrawReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrolled_by", nullable = false)
    private User enrolledBy;

    @Column(name = "import_job_id")
    private Long importJobId;

    /** Copy từ schoolClass.academicYear tại thời điểm tạo (V102/V103, bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;
}
