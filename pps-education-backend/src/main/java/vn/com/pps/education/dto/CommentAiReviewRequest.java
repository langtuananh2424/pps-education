package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** UC-75 bước 1 — Quản lý điểm trường bấm "Soát bằng AI" cho các nhận xét đang chờ duyệt (thường cả 1 lớp). */
public record CommentAiReviewRequest(@NotEmpty @Size(max = 300) List<@NotNull Long> commentIds) {
}
