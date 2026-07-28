package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** UC-23b: dùng chung cho cả Học sinh xem bài của mình và Giáo viên xem danh sách/chấm điểm. 1 dòng = 1 attempt (giữ lịch sử, V57). */
public record ReviewVideoSubmissionResponse(
        Long id,
        Long reviewVideoQuestionId,
        int attemptNumber,
        Long studentId,
        String studentFullName,
        String audioUrl,
        OffsetDateTime submittedAt,
        BigDecimal score,
        BigDecimal maxScore,
        String feedback,
        Long gradedByUserId,
        OffsetDateTime gradedAt
) {}
