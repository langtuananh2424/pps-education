package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** UC-67 bước 3: cấu hình toàn bộ ánh xạ cho 1 mẫu (thay thế hoàn toàn danh sách cũ). */
public record UpdateFieldMappingsRequest(
        @NotEmpty @Valid List<FieldMappingItemRequest> mappings
) {}
