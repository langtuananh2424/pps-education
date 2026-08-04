package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * UC-22 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-02) —
 * DTO dành RIÊNG cho Quản lý điểm trường sửa nội dung nhận xét đang PENDING
 * trước khi duyệt. KHÔNG gộp với UpdateStudentCommentRequest (dùng cho GV)
 * để tránh rủi ro vô tình cập nhật các field BTVN (VD
 * homeworkNextExerciseId) gây side-effect tự động giao bài mới cho cả lớp.
 */
public record UpdateStudentCommentContentRequest(
        @NotBlank String content,
        Map<String, Object> structuredContent
) {}
