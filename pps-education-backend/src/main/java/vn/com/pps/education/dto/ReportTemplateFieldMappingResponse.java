package vn.com.pps.education.dto;

public record ReportTemplateFieldMappingResponse(
        Long id,
        String placeholderKey,
        String dataPath,
        String fieldType,
        String description
) {}
