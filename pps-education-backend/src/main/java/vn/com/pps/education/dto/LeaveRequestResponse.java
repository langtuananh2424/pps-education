package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record LeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeFullName,
        String employeeCode,
        String departmentName,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal totalDays,
        String reason,
        String attachmentUrl,
        String status,
        int currentStep,
        Long currentApproverUserId,
        OffsetDateTime submittedAt,
        OffsetDateTime finalizedAt
) {}
