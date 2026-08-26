package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng homework_skill_batches (V150, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) —
 * "Lô giao BTVN theo kỹ năng": 1 lô = 1 lần giáo viên chọn "(Lesson, Kỹ năng)" ở UC-21 để giao cho 1
 * lớp. KHÔNG chứa nội dung câu hỏi — chỉ là lớp gom mỏng ở trên N {@link ExerciseAssignment} thật (1
 * bản giao/Bài cùng skillCategory trong exam đó, xem {@link ExerciseAssignment#getHomeworkBatch()}).
 * Mỗi bản giao con vẫn đi nguyên luồng giao/chấm/nhắc hạn cũ (xem HomeworkSkillBatchService).
 */
@Getter
@Setter
@Entity
@Table(name = "homework_skill_batches")
public class HomeworkSkillBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_category", nullable = false, length = 20)
    private Exercise.SkillCategory skillCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    /** V123 mirror (xem {@link ExerciseAssignment#getSourceClassSession()}) — buổi học lúc giao, NULL nếu không rõ. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_class_session_id")
    private ClassSession sourceClassSession;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
