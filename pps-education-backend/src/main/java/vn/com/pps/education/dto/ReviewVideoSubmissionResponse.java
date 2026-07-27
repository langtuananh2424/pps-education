package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** UC-23b: dùng chung cho cả Học sinh xem bài của mình và Giáo viên xem danh sách/chấm điểm. */
public record ReviewVideoSubmissionResponse(
        Long id,
        Long reviewVideoId,
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
