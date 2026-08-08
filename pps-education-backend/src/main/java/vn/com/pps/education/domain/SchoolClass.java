package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng classes (SDD > Học thuật > Khung chương trình & Lớp học > c) — lớp
 * học thực tế (UC-18). Đặt tên entity SchoolClass (không phải "Class") để
 * tránh nhầm lẫn với java.lang.Class.
 */
@Getter
@Setter
@Entity
@Table(name = "classes")
public class SchoolClass extends BaseAuditEntity {

    public enum ClassType { LINKED, OPEN }

    public enum Status { PLANNED, OPEN_ENROLLMENT, IN_PROGRESS, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(name = "class_code", nullable = false, unique = true, length = 50)
    private String classCode;

    @Column(nullable = false, length = 300)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false, length = 20)
    private ClassType classType;

    /** Copy từ curriculum.classCategory tại thời điểm tạo (SDD). */
    @Column(name = "class_category", length = 30)
    private String classCategory;

    @Column(name = "max_students", nullable = false)
    private int maxStudents;

    @Column(name = "min_students")
    private Integer minStudents;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PLANNED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
