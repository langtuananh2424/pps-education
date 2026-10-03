package vn.com.pps.education.student.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AttendanceSessionResponse(
        Long id,
        Long classSessionId,
        String mode,
        Long markedBy,
        OffsetDateTime markedAt,
        String status,
        OffsetDateTime submittedAt,
        List<AttendanceMarkResponse> marks
) {}
