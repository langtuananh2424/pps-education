package vn.com.pps.education.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kết quả xem trước file Excel BTVN — CHỈ trả dữ liệu đã parse để Giáo viên
 * fill vào bảng nhận xét trên UI, KHÔNG ghi StudentComment/Bài học hôm nay/
 * Tên GV giảng dạy/Hạn nộp vào DB (khác {@code DailyCommentImportResponse},
 * dùng cho luồng import cũ ghi thẳng DB). Điểm danh vẫn được ghi ngay ở
 * bước preview này (nghiệp vụ độc lập, không thuộc quy trình soạn/duyệt
 * nhận xét). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 —
 * xem Javadoc {@code StudentCommentService#previewImportComments}.
 */
public record DailyCommentImportPreviewResponse(
        int totalRows,
        int successRows,
        int failedRows,
        List<Map<String, Object>> errorSummary,
        String lessonContent,
        String teacherName,
        LocalDateTime dueDate,
        List<DailyCommentImportPreviewRow> rows
) {}
