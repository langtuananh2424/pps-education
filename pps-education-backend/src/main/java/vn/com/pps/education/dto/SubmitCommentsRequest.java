package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** UC-21 Main Flow bước 4: submit từng nhận xét (1 phần tử) hoặc theo lô (nhiều phần tử). */
public record SubmitCommentsRequest(
        @NotEmpty List<Long> commentIds
) {}
