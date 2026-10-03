package vn.com.pps.education.student.dto;

public record ParentResponse(
        Long id,
        Long userId,
        String fullName,
        String occupation,
        String workplace,
        String address,
        String notes,
        String portraitUrl
) {}
