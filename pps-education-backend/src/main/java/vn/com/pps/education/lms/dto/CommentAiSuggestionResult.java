package vn.com.pps.education.lms.dto;

import java.util.List;

/**
 * UC-75 bước 6-7 — bản sửa AI đề xuất cho 1 nhận xét chờ duyệt. CHƯA ghi DB: Quản lý bấm "Áp dụng" thì FE
 * gọi đúng endpoint sửa nội dung PENDING sẵn có của UC-22.
 */
public record CommentAiSuggestionResult(Long commentId, String originalContent, String suggestedContent,
                                        String explanation, List<String> warnings) {
}
