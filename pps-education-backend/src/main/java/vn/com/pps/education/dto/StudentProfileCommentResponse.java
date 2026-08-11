package vn.com.pps.education.dto;

import java.time.LocalDate;

/** Bổ sung ngoài SDD gốc — 1 nhận xét trong StudentProfileResponse (FR-REP-04). academicTermName/sessionNumber/sessionDate suy ra ở Service khi cần (DAILY không có sẵn academicTermId). */
public record StudentProfileCommentResponse(
        Long id,
        Long classId,
        String className,
        String commentType,
        LocalDate commentDate,
        String content,
        String severity,
        boolean isWarning,
        String attitude,
        String status,
        Long academicTermId,
        String academicTermName,
        String academicYear,
        Long classSessionId,
        Integer sessionNumber,
        LocalDate sessionDate
) {
}
