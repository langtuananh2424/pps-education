package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** UC-22 Main Flow bước 2-3, A1 (duyệt lô nhanh — truyền nhiều id cùng lúc). */
public record DecideCommentsRequest(
        @NotEmpty List<Long> commentIds,
        @NotBlank String decision,
        String comment
) {}
