package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** UC-75 bước 6 — các cảnh báo (lấy từ kết quả soát) cần AI sửa cho 1 nhận xét chờ duyệt. */
public record CommentAiSuggestionRequest(@Size(max = 20) List<@Size(max = 500) String> issues) {
}
