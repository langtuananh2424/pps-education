package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Bảng question_banks (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập >
 * a) — ngân hàng câu hỏi dùng chung, nền tảng cho UC-40 (soạn đề).
 */
@Getter
@Setter
@Entity
@Table(name = "question_banks")
public class QuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    @Column(length = 50)
    private String level;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
