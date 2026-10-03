package vn.com.pps.education.hr.dto;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        Long headUserId,
        String headUserFullName,
        Long parentDepartmentId,
        String parentDepartmentName
) {}
