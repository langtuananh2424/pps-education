package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * UC-23b: dùng chung cho cả Học sinh xem bài của mình và Giáo viên xem danh sách/chấm điểm. 1 dòng = 1 attempt (giữ lịch sử, V57).
 * 7 field cuối (bổ sung ngoài SDD gốc, xác nhận 2026-08-17) chỉ được điền khi trả về từ hàng chờ chấm GỘP theo lớp
 * (ReviewVideoService#listSubmissionsForTeacherByClass) — null ở mọi nơi khác (học sinh xem bài mình, chấm 1 bài,
 * hàng chờ theo đúng 1 Bộ+Lớp) vì caller ở các luồng đó đã biết sẵn Bộ/Lớp đang xem, không cần lặp lại trong từng dòng.
 */
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
        OffsetDateTime gradedAt,
        Long reviewVideoSetId,
        String reviewVideoSetTitle,
        Long reviewVideoId,
        String reviewVideoTitle,
        Integer reviewVideoDisplayOrder,
        String questionPrompt,
        Integer timestampSeconds
) {}
