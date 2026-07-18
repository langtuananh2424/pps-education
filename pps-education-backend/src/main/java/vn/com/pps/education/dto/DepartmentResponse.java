package vn.com.pps.education.dto;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        Long headUserId,
        String headUserFullName,
        Long parentDepartmentId,
        String parentDepartmentName
) {}
