package vn.com.pps.education.hr.dto;

import vn.com.pps.education.permission.dto.RoleResponse;

import java.util.List;

/** FR-HRM-06/UC-52 — danh sách role mặc định hiện cấu hình cho 1 chức vụ. */
public record PositionDefaultRolesResponse(
        Long positionId,
        String positionCode,
        List<RoleResponse> defaultRoles
) {}
