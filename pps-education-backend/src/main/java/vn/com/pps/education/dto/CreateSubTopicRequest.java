package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** V144: thêm 1 Sub Topic vào 1 Unit. */
public record CreateSubTopicRequest(
        @NotBlank String title,
        Integer displayOrder
) {}
