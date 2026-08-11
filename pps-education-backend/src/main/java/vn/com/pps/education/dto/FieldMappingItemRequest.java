package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-67 bước 3: 1 dòng ánh xạ cho 1 placeholder. dataPath bắt buộc khi
 * fieldType=FIELD; bỏ trống (NULL) khi fieldType=FORMULA/TABLE (không cấu
 * hình data_path riêng — hệ thống tự resolve từ chính placeholderKey).
 */
public record FieldMappingItemRequest(
        @NotBlank String placeholderKey,
        String dataPath,
        String description
) {}
