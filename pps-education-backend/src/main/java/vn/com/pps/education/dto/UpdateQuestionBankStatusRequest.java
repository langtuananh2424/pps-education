package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** Bổ sung — is_active tồn tại sẵn trong SDD nhưng trước đây không endpoint nào set được. */
public record UpdateQuestionBankStatusRequest(
        @NotNull Boolean isActive
) {}
