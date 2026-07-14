package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.support.AbstractControllerTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-43 + UC-45 A4: xác nhận user.manage (Hybrid PBAC) chặn/cho phép đúng
 * qua HTTP thật cho cả tạo tài khoản và đổi mật khẩu tài khoản khác.
 */
@Transactional
class UserControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void create_deniedForRoleWithoutUserManage_returns403() throws Exception {
        var staff = userWithRole("staff.noaccess", "STAFF");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUserRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void create_allowedForSysAdmin_returns200() throws Exception {
        var sysAdmin = userWithRole("sysadmin.access", "SYS_ADMIN");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUserRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_UC45_A4_deniedForRoleWithoutUserManage_returns403() throws Exception {
        var staff = userWithRole("staff.pwd.noaccess", "STAFF");
        User target = userWithRole("target.pwd.noaccess", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/password")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChangePasswordRequest("MatKhauMoi@9"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void changePassword_UC45_A4_allowedForSysAdmin_returns204() throws Exception {
        var sysAdmin = userWithRole("sysadmin.pwd.access", "SYS_ADMIN");
        User target = userWithRole("target.pwd.access", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/password")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChangePasswordRequest("MatKhauMoi@9"))))
                .andExpect(status().isNoContent());
    }

    private CreateUserRequest newUserRequest() {
        long n = SEQ.incrementAndGet();
        return new CreateUserRequest("user.api." + n, "user.api." + n + "@pps.edu.vn",
                "Người Test API", null, "MatKhau@8", null, null);
    }
}
