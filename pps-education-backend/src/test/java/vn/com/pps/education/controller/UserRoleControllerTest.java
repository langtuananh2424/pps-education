package vn.com.pps.education.controller;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-46: xác nhận user.role.manage (Hybrid PBAC) chặn/cho phép đúng qua HTTP thật.
 */
@Transactional
class UserRoleControllerTest extends AbstractControllerTest {

    // HR_MANAGER = role id 6 (thứ tự seed ở V4__seed_roles_and_permissions.sql).
    private static final long HR_MANAGER_ROLE_ID = 6L;

    @Test
    void assignRole_deniedForRoleWithoutUserRoleManage_returns403() throws Exception {
        var staff = userWithRole("staff.role.noaccess", "STAFF");
        User target = userWithRole("target.role.noaccess", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/roles/" + HR_MANAGER_ROLE_ID)
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void assignRole_allowedForSysAdmin_returns204AndListReflectsIt() throws Exception {
        var sysAdmin = userWithRole("sysadmin.role.access", "SYS_ADMIN");
        User target = userWithRole("target.role.access", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/roles/" + HR_MANAGER_ROLE_ID)
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + target.getId() + "/roles")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='HR_MANAGER')]").exists());
    }

    @Test
    void revokeRole_deniedForRoleWithoutUserRoleManage_returns403() throws Exception {
        var staff = userWithRole("staff.role.revoke.noaccess", "STAFF");
        User target = userWithRole("target.role.revoke.noaccess", "STUDENT");

        mockMvc.perform(delete("/api/users/" + target.getId() + "/roles/" + HR_MANAGER_ROLE_ID)
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeRole_UC46_A2_notAssigned_returns404() throws Exception {
        var sysAdmin = userWithRole("sysadmin.role.revoke404", "SYS_ADMIN");
        User target = userWithRole("target.role.revoke404", "STUDENT");

        mockMvc.perform(delete("/api/users/" + target.getId() + "/roles/" + HR_MANAGER_ROLE_ID)
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN")))
                .andExpect(status().isNotFound());
    }
}
