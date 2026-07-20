package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-56: Sinh lịch học hàng loạt (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng). skipped: mỗi phần tử {"date": "...", "reason": "..."} —
 * ngày trùng phòng bị bỏ qua, các ngày khác trong lô vẫn tạo bình thường
 * (giống pattern lỗi-từng-dòng của batch import UC-35/50/51/53).
 */
public record BulkCreateClassSessionResponse(
        int totalDates,
        int createdCount,
        int skippedCount,
        List<ClassSessionResponse> created,
        List<Map<String, Object>> skipped
) {}
