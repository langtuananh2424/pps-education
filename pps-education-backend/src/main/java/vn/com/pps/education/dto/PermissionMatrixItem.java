package vn.com.pps.education.dto;

/** 1 dòng trong ma trận quyền của UC-03 bước 2 — granted = quyền này đang được gán cho role. */
public record PermissionMatrixItem(
        Long permissionId,
        String code,
        String name,
        String module,
        boolean granted
) {}
