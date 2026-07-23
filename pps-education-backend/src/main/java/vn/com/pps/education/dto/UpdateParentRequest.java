package vn.com.pps.education.dto;

/** Cập nhật thông tin phụ huynh đã có. */
public record UpdateParentRequest(
        String occupation,
        String workplace,
        String address,
        String notes,
        String portraitUrl
) {}
