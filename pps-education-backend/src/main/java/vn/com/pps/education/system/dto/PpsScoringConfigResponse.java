package vn.com.pps.education.system.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình bộ chấm PPS Hybrid v2 để vẽ biểu mẫu: mỗi tham số kèm nhãn, nhóm, khoảng cho phép, mặc định và giá trị đang dùng.
 *
 * @param overrides    các tham số khác mặc định (phẳng); rỗng là đang dùng mặc định
 * @param weightGroups tiền tố các nhóm trọng số phải cộng bằng 1
 */
public record PpsScoringConfigResponse(
        List<Field> fields,
        Map<String, Double> overrides,
        List<String> weightGroups,
        String updatedByName,
        OffsetDateTime updatedAt
) {

    public record Field(String path, String group, String label, String help, double min, double max, double step,
                        double defaultValue, double value, boolean changed) {
    }
}
