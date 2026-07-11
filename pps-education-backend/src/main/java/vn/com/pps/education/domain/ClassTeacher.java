package vn.com.pps.education.domain;

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

    public enum TeacherRole { PRIMARY, ASSISTANT, SUBSTITUTE }

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

    @Column(name = "assigned_from")
    private LocalDate assignedFrom;

    /** NULL = đang phụ trách (SDD). */
    @Column(name = "assigned_to")
    private LocalDate assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;
}
