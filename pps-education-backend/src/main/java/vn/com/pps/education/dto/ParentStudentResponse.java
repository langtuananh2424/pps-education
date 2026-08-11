package vn.com.pps.education.dto;

public record ParentStudentResponse(
        Long id,
        Long parentId,
        String parentFullName,
        String parentPhone,
        Long studentId,
        String relationship,
        boolean isPrimaryContact,
        boolean isFinancialResponsible,
        String notes
) {}
