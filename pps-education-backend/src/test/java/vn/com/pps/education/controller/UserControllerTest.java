package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.UpdateUserEmailRequest;
import vn.com.pps.education.dto.UpdateUserRequest;
import vn.com.pps.education.dto.UpdateUserStatusRequest;
import vn.com.pps.education.support.AbstractControllerTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-43 + UC-45 A4: xác nhận user.create/user.update (Hybrid PBAC, tách từ
 * user.manage ở V62) chặn/cho phép đúng qua HTTP thật cho cả tạo tài
 * khoản và đổi mật khẩu tài khoản khác.
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

    @Test
    void updateEmail_UC55_deniedForRoleWithoutUserManage_returns403() throws Exception {
        var staff = userWithRole("staff.email.noaccess", "STAFF");
        User target = userWithRole("target.email.noaccess", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/email")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserEmailRequest("moi." + SEQ.incrementAndGet() + "@gmail.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void updateEmail_UC55_allowedForSysAdmin_returns200() throws Exception {
        var sysAdmin = userWithRole("sysadmin.email.access", "SYS_ADMIN");
        User target = userWithRole("target.email.access", "STUDENT");
        String newEmail = "moi." + SEQ.incrementAndGet() + "@gmail.com";

        mockMvc.perform(put("/api/users/" + target.getId() + "/email")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserEmailRequest(newEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void search_UC44_deniedForRoleWithoutUserManage_returns403() throws Exception {
        var staff = userWithRole("staff.search.noaccess", "STAFF");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void search_UC44_allowedForSysAdmin_returns200() throws Exception {
        var sysAdmin = userWithRole("sysadmin.search.access", "SYS_ADMIN");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void getDetail_UC44_allowedForSysAdmin_returns200() throws Exception {
        var sysAdmin = userWithRole("sysadmin.detail.access", "SYS_ADMIN");
        User target = userWithRole("target.detail.access", "STUDENT");

        mockMvc.perform(get("/api/users/" + target.getId())
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()));
    }

    @Test
    void update_UC49_deniedForRoleWithoutUserManage_returns403() throws Exception {
        var staff = userWithRole("staff.update.noaccess", "STAFF");
        User target = userWithRole("target.update.noaccess", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId())
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("Tên Mới", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void update_UC49_allowedForSysAdmin_returns200() throws Exception {
        var sysAdmin = userWithRole("sysadmin.update.access", "SYS_ADMIN");
        User target = userWithRole("target.update.access", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId())
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("Tên Mới", "0911111111"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Tên Mới"));
    }

    @Test
    void updateStatus_UC47_deniedForRoleWithoutUserManage_returns403() throws Exception {
        var staff = userWithRole("staff.status.noaccess", "STAFF");
        User target = userWithRole("target.status.noaccess", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/status")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest("SUSPENDED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void updateStatus_UC47_allowedForSysAdmin_returns200() throws Exception {
        var sysAdmin = userWithRole("sysadmin.status.access", "SYS_ADMIN");
        User target = userWithRole("target.status.access", "STUDENT");

        mockMvc.perform(put("/api/users/" + target.getId() + "/status")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest("SUSPENDED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updateStatus_UC47_A2_selfLockReturns403() throws Exception {
        var sysAdmin = userWithRole("sysadmin.selflock.access", "SYS_ADMIN");

        mockMvc.perform(put("/api/users/" + sysAdmin.getId() + "/status")
                        .header("Authorization", bearerToken(sysAdmin, "SYS_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest("SUSPENDED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không thể tự khóa tài khoản của chính mình."));
    }

    private CreateUserRequest newUserRequest() {
        long n = SEQ.incrementAndGet();
        return new CreateUserRequest("user.api." + n, "user.api." + n + "@pps.edu.vn",
                "Người Test API", null, "MatKhau@8");
    }
}
