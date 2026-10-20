package vn.com.pps.education.lms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Chấm lần 2 (bổ sung ngoài SDD gốc, xác nhận 2026-10-19) — 1 bài/1 bước do AI chấm, kèm điểm gốc của AI và
 * (nếu có) điểm giáo viên đã chấm lại. {@code itemType}: REFLEX_WRITING / REFLEX_SPEAKING (refId =
 * reflex_question_progress.id) hoặc ESSAY (refId = student_answers.id).
 *
 * @param aiScore          điểm GỐC của AI (kể cả khi giáo viên đã chấm lại)
 * @param reviewedScore    điểm giáo viên chấm lại — null khi chưa chấm lại
 * @param maxScore         thang điểm (100 với Video phản xạ — điểm %, hoặc điểm tối đa của câu ESSAY)
 */
public record AiGradedItemResponse(
        String itemType,
        Long refId,
        Long studentId,
        String studentFullName,
        String sourceTitle,
        String prompt,
        String studentText,
        String markedText,
        String audioUrl,
        BigDecimal aiScore,
        BigDecimal maxScore,
        String aiFeedback,
        OffsetDateTime aiGradedAt,
        boolean reviewed,
        BigDecimal reviewedScore,
        String reviewedFeedback,
        OffsetDateTime reviewedAt,
        String reviewerName
) {}
