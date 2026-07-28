package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;

public record UpdateGradeAppealWindowRequest(@Min(1) int days) {
}
