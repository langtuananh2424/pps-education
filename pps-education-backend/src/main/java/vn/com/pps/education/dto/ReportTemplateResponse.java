package vn.com.pps.education.dto;

import java.util.List;

public record ReportTemplateResponse(
        Long id,
        String name,
        String templateType,
        String description,
        String fileUrl,
        String fileFormat,
        String originalFilename,
        long fileSizeBytes,
        List<String> placeholderKeys,
        boolean active,
        Long createdBy,
        List<ReportTemplateFieldMappingResponse> fieldMappings
) {}
