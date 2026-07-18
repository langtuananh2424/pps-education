package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * UC-21 Main Flow bước 1-3: viết nhận xét học sinh. classSessionId bắt
 * buộc khi commentType=DAILY; gradePeriodId bắt buộc khi commentType=
 * MID_TERM/END_TERM (SDD chk_comment_context — validate lại ở Service).
 */
public record CreateStudentCommentRequest(
        @NotNull Long studentId,
        @NotBlank String commentType,
        Long classSessionId,
        Long gradePeriodId,
        @NotNull LocalDate commentDate,
        @NotBlank String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning
) {}
