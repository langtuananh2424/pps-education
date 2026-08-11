package vn.com.pps.education.dto;

/**
 * UC-67 bước 1: metadata đi kèm file upload (multipart) khi tạo mẫu báo
 * cáo mới. Nhận qua @RequestParam (không @Valid — xem
 * ReportTemplateController#create), validate thủ công trong
 * ReportTemplateService.
 */
public record CreateReportTemplateRequest(
        String name,
        String templateType,
        String description
) {}
