package vn.com.pps.education.academic.domain;

import vn.com.pps.education.auth.domain.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bảng class_teachers (SDD > Học thuật > Khung chương trình & Lớp học >
 * d) — gán giáo viên cho lớp (UC-18 Main Flow bước 1-2, TPĐT quyết định
 * điều phối giáo viên).
 */
@Getter
@Setter
@Entity
@Table(name = "class_teachers")
public class ClassTeacher {

    /**
     * SCHEDULED = "Dạy theo lịch" (V209 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-02):
     * hệ thống TỰ tạo khi giáo viên được xếp vào 1 buổi học (chính/phụ/CM) của lớp mà chưa có phân
     * công nào đang hiệu lực, và tự kết thúc khi giáo viên không còn buổi nào trong khoảng
     * system_settings.academic.scheduled_teacher_revoke_days ngày — xem ScheduledTeacherAssignmentService.
     * Không gán tay được qua UC-18.
     */
    public enum TeacherRole { PRIMARY, ASSISTANT, SUBSTITUTE, CM, SCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_user_id", nullable = false)
    private User teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_role", nullable = false, length = 20)
    private TeacherRole teacherRole = TeacherRole.PRIMARY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    /**
     * Loại giáo viên (Việt Nam/nước ngoài) — bổ sung ngoài SDD gốc, xác
     * nhận 2026-08-13, chỉ có ý nghĩa khi teacherRole=PRIMARY (1 lớp có
     * đồng thời tối đa 1 PRIMARY active loại VIETNAMESE + 1 PRIMARY active
     * loại FOREIGN). Tái dùng {@link ClassSession.TeacherType}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_type", length = 20)
    private ClassSession.TeacherType teacherType;

    @Column(name = "assigned_from")
    private LocalDate assignedFrom;

    /** NULL = đang phụ trách (SDD). */
    @Column(name = "assigned_to")
    private LocalDate assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;
}
