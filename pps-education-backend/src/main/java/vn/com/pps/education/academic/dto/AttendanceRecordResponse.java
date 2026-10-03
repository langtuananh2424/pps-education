package vn.com.pps.education.academic.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AttendanceRecordResponse(
        Long id,
        Long employeeId,
        LocalDate workDate,
        OffsetDateTime checkInAt,
        OffsetDateTime checkOutAt,
        String checkInMethod,
        String checkOutMethod,
        Long siteId,
        String status
) {}
