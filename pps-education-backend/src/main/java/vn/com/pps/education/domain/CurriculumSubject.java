package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng curriculum_subjects (SDD > Học thuật > Khung chương trình & Lớp
 * học > b) — học phần trong khung chương trình (UC-16 Main Flow bước 2).
 */
@Getter
@Setter
@Entity
@Table(name = "curriculum_subjects")
public class CurriculumSubject {

    public enum SubjectCode { SPEAKING, LISTENING, READING, WRITING, GRAMMAR, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_code", nullable = false, length = 50)
    private SubjectCode subjectCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "period_count")
    private Integer periodCount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
