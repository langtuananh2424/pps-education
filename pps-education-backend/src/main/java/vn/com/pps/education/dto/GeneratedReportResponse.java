package vn.com.pps.education.dto;

public record GeneratedReportResponse(
        Long id,
        Long templateId,
        String scope,
        Long studentId,
        Long classId,
        Long classSessionId,
        String fileUrl,
        String fileFormat,
        long fileSizeBytes,
        Long generatedBy
) {}
