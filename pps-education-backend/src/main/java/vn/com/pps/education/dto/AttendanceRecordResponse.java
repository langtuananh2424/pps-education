package vn.com.pps.education.dto;

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
