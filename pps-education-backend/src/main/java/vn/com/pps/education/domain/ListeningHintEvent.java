package vn.com.pps.education.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng listening_hint_events (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-06 — xem migration V94) — 1 dòng/lần học sinh THỰC
 * SỰ mở gợi ý tapescript ra xem (sau khi đã nghe hết audio đủ số lần cấu
 * hình), phục vụ thống kê sau này câu nào học sinh cần gợi ý nhiều nhất.
 * Log bất biến (không kế thừa BaseAuditEntity, giống AttemptIntegrityEvent/
 * ExerciseAttemptHistory) — chỉ thêm dòng mới, không sửa/xóa.
 */
@Getter
@Setter
@Entity
@Table(name = "listening_hint_events")
public class ListeningHintEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_attempt_id", nullable = false)
    private ExerciseAttempt exerciseAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
