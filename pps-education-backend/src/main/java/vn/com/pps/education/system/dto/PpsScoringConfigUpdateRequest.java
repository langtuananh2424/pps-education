package vn.com.pps.education.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Lưu cấu hình: {@code config} là TOÀN BỘ cấu hình mong muốn (lồng nhau hoặc khoá phẳng "a.b.c"); tham số không có trong đó trở về mặc
 * định. Chấp nhận cả file xuất ra bọc trong {@code {"config": ...}}.
 */
public record PpsScoringConfigUpdateRequest(
        @NotNull Map<String, Object> config,
        @Size(max = 500) String note
) {
}
