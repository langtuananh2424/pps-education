package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng listening_practice_items (UC-26, FR-LMS-04 — bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng). 1 bài luyện nghe/chép chính tả/nói,
 * gắn theo curriculum, học sinh tự luyện không deadline.
 */
@Getter
@Setter
@Entity
@Table(name = "listening_practice_items")
public class ListeningPracticeItem extends BaseAuditEntity {

    public enum Mode { LISTENING, DICTATION, SPEAKING }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode;

    /** NULL = FE tự đọc bằng Web Speech API trình duyệt theo scriptText. */
    @Column(name = "audio_url", length = 1000)
    private String audioUrl;

    @Column(name = "script_text", nullable = false, columnDefinition = "TEXT")
    private String scriptText;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Difficulty difficulty;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
