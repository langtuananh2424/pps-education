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
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng listening_play_progress (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-06 — xem migration V94) — đếm số lần học sinh nghe
 * HẾT 1 audio (câu hỏi Nghe, skill=LISTENING) trong 1 lượt làm bài, làm
 * điều kiện mở khóa gợi ý tapescript. Xem Javadoc ListeningHintService.
 *
 * listeningKey: nhóm "1 audio nhiều câu" (ListeningGroupBuilder, FE) dùng
 * CHUNG 1 dòng tiến độ (server tự suy từ Question.groupKey nếu có, không
 * thì "Q"+questionId) — không tách theo từng câu hỏi con.
 */
@Getter
@Setter
@Entity
@Table(name = "listening_play_progress")
public class ListeningPlayProgress extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_attempt_id", nullable = false)
    private ExerciseAttempt exerciseAttempt;

    @Column(name = "listening_key", nullable = false, length = 80)
    private String listeningKey;

    @Column(name = "play_count", nullable = false)
    private int playCount = 0;
}
