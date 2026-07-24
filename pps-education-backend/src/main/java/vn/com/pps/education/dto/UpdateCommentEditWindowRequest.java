package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;

public record UpdateCommentEditWindowRequest(@Min(1) int days) {
}
