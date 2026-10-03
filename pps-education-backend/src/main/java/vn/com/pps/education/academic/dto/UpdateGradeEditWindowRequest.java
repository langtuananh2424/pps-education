package vn.com.pps.education.academic.dto;

import jakarta.validation.constraints.Min;

public record UpdateGradeEditWindowRequest(@Min(1) int days) {
}
