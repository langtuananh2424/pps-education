package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Bảng teaching_plan_items (SDD > LMS & Portal > Kế hoạch giảng dạy > b) — chi tiết từng buổi/tuần trong kế hoạch. Không có history. */
@Getter
@Setter
@Entity
@Table(name = "teaching_plan_items")
public class TeachingPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teaching_plan_id", nullable = false)
    private TeachingPlan teachingPlan;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(nullable = false, length = 500)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Column(name = "content_outline", columnDefinition = "TEXT")
    private String contentOutline;

    @Column(name = "skills_focus", length = 200)
    private String skillsFocus;

    @Column(name = "homework_note", columnDefinition = "TEXT")
    private String homeworkNote;

    /** Liên kết buổi học cụ thể nếu có (SDD). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id")
    private ClassSession classSession;
}
