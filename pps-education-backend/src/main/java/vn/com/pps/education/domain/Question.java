package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng questions (SDD > LMS & Portal > Ngân hàng câu hỏi & Bài tập > b).
 * Sửa khi đã có student_answers: cấm sửa content/đáp án đúng, phải tạo
 * bản mới + archive bản cũ (SDD) — xem QuestionBankService.updateQuestion().
 */
@Getter
@Setter
@Entity
@Table(name = "questions")
public class Question {

    public enum QuestionType { MULTIPLE_CHOICE, MULTIPLE_ANSWER, TRUE_FALSE, FILL_IN_BLANK, ESSAY, SPEAKING, WORD_BANK, SENTENCE_BUILDING }

    public enum Skill { LISTENING, READING, WRITING, SPEAKING, GRAMMAR, OTHER }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public enum Status { ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_bank_id", nullable = false)
    private QuestionBank questionBank;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Difficulty difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "reference_passage", columnDefinition = "TEXT")
    private String referencePassage;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /**
     * Chỉ có ý nghĩa khi questionType=FILL_IN_BLANK — so khớp CHÍNH XÁC
     * (case-insensitive + trim) khi tự chấm, xem
     * ExerciseAttemptService.isAnswerCorrect(). Bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng — xem V54.
     */
    @Column(name = "correct_answer_text", columnDefinition = "TEXT")
    private String correctAnswerText;

    /**
     * V85 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dữ liệu riêng theo
     * questionType: WORD_BANK dùng key "blanks" (mảng đáp án đúng theo đúng thứ tự chỗ trống trong
     * content); SENTENCE_BUILDING dùng key "chunks" (mảng khối từ/cụm theo ĐÚNG thứ tự câu hoàn
     * chỉnh — FE tự xáo trộn lúc hiển thị cho học sinh). Rỗng với mọi questionType khác.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_content", columnDefinition = "jsonb")
    private Map<String, Object> structuredContent;

    /**
     * V85 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dạng "Đọc hiểu — lưới":
     * nhiều câu MULTIPLE_CHOICE cùng 1 groupKey được gộp hiển thị chung 1 referencePassage (cả lúc
     * soạn lẫn lúc học sinh làm bài) — xem GridQuestionBuilder (FE) / TakeExerciseModal (Portal).
     */
    @Column(name = "group_key", length = 64)
    private String groupKey;

    @Column(name = "default_points", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultPoints = new BigDecimal("1.0");

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * Key Grammar (filter 2, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22, V186) — mảng
     * mã cấu trúc (VD {@code ["pres_perf","past_perf"]}), 1-3 phần tử, khớp từ điển đúng Khối/track của
     * {@code questionBank.curriculum} (xem {@link vn.com.pps.education.service.KeyGrammarDictionaryLoader}).
     * Chỉ có ý nghĩa khi {@code questionType=ESSAY} — validate ở QuestionBankService, không CHECK
     * constraint DB. {@code null} = không kiểm Key Grammar cho câu hỏi này (mặc định).
     *
     * Gắn vào CÂU HỎI (không phải Bài/Exercise) — set 1 lần trong modal "Sửa câu hỏi", áp dụng cho MỌI
     * lượt giao Bài chứa câu hỏi này sau này (giao nhanh lẫn qua Nhận xét học viên UC-21), đã xác nhận
     * với người dùng 2026-09-22 sau khi xem qua UI thật: Key Grammar đi cùng đúng đề bài tự luận cụ thể,
     * không phải cấu hình chung của cả Bài. An toàn khi 1 câu hỏi được TÁI SỬ DỤNG ở nhiều Bài khác nhau
     * (qua {@code exercise_questions}) — {@code questionBank.curriculum} cố định 1 Khối/track duy nhất
     * cho mọi Bài dùng lại câu hỏi này (xem ExerciseService#addQuestion, ràng buộc questionBank khớp),
     * nên từ điển Key Grammar tra ra luôn đúng bất kể Bài nào đang dùng.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_grammar", columnDefinition = "jsonb")
    private List<String> keyGrammar;
}
