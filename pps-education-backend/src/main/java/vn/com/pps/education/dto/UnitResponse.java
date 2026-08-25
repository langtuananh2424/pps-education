package vn.com.pps.education.dto;

public record UnitResponse(
        Long id,
        Long bookId,
        String title,
        int displayOrder
) {}
