package vn.com.pps.education.permission.dto;

import jakarta.validation.constraints.NotNull;
import vn.com.pps.education.permission.domain.Role;

/** V202 — đổi phạm vi dữ liệu của 1 vai trò trên màn "Nhóm vai trò". */
public record UpdateRoleDataScopeRequest(
        @NotNull Role.DataScope dataScope
) {}
