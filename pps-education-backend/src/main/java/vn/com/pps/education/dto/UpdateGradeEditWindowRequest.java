package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;

public record UpdateGradeEditWindowRequest(@Min(1) int days) {
}
