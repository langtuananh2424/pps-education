package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng teaching_plans (SDD > LMS & Portal > Kế hoạch giảng dạy > a) — kế
 * hoạch giảng dạy theo tuần/năm học (UC-28 FR-LMS-08). Không có bước
 * duyệt — GV lưu (DRAFT) hoặc gửi (PUBLISHED) là hiển thị ngay cho Portal
 * trường liên kết (UC-29), không qua ApprovalFlow.
 */
@Getter
@Setter
@Entity
@Table(name = "teaching_plans")
public class TeachingPlan {

    public enum PlanType { WEEKLY, YEARLY }

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_user_id", nullable = false)
    private User teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    /** Cho YEARLY (SDD). V103 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07): đổi từ chuỗi tự do sang FK academic_years. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    /** Cho WEEKLY (SDD). */
    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "week_end_date")
    private LocalDate weekEndDate;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "visible_to_partner", nullable = false)
    private boolean visibleToPartner = true;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
