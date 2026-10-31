package vn.com.pps.education.system.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** Một lần sửa cấu hình: {@code previous} và {@code overrides} là các tham số khác mặc định (phẳng) trước và sau khi lưu. */
public record PpsScoringConfigHistoryResponse(
        Long id,
        Map<String, Double> previous,
        Map<String, Double> overrides,
        String changedByName,
        String note,
        OffsetDateTime createdAt
) {
}
