package vn.com.pps.education.dto;

import java.util.List;

/** FR-HRM-06/UC-52 — danh sách role mặc định hiện cấu hình cho 1 chức vụ. */
public record PositionDefaultRolesResponse(
        Long positionId,
        String positionCode,
        List<RoleResponse> defaultRoles
) {}
