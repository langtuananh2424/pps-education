package vn.com.pps.education.lms.dto;

public record SkillResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active) {
}
