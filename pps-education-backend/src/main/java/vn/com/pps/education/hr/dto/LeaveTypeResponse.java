package vn.com.pps.education.hr.dto;

public record LeaveTypeResponse(
    String code,
    String label,
    Integer sortOrder
) {}
