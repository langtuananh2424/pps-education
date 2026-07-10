package vn.com.pps.education.dto;

import java.util.Set;

/** UC-04 bước 2: quyền hiệu lực hiện tại của 1 tài khoản. */
public record EffectivePermissionsResponse(
        Long userId,
        Set<String> permissions
) {}
