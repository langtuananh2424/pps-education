package vn.com.pps.education.academic.dto;

public record UnitResponse(
        Long id,
        Long bookId,
        String title,
        int displayOrder
) {}
