package vn.com.pps.education.dto;

import java.util.Set;

/** FR-HRM-06/UC-52 — thay thế TOÀN BỘ danh sách role mặc định của 1 chức vụ (tập rỗng = bỏ hết). */
public record UpdatePositionDefaultRolesRequest(
        Set<Long> roleIds
) {}
