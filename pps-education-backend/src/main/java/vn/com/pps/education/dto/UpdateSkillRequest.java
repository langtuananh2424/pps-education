package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSkillRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull Boolean active) {
}
