package vn.com.pps.education.dto;

public record AvailableReportFieldResponse(
        String key,
        String label,
        String description,
        String fieldType
) {}
