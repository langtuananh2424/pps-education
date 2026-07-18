package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/auth/me: yêu cầu JWT hợp lệ (SecurityConfig — rule cụ thể hơn
 * "/api/auth/**" permitAll), nhưng KHÔNG cần permission code cụ thể — bất
 * kỳ role nào cũng gọi được (đọc hồ sơ chính mình).
 *
 * PUT /api/auth/me/password: UC-45 Main Flow — cùng lý do, tự đổi mật khẩu
 * chính mình không cần permission code, chỉ cần JWT hợp lệ.
 */
@Transactional
class AuthControllerTest extends AbstractControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void me_deniedWithoutJwt_returns403() throws Exception {
        // Không có AuthenticationEntryPoint riêng trong SecurityConfig -> Spring Security
        // mặc định trả 403 (không phải 401) cho request chưa xác thực bị chặn bởi authenticated().
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_allowedForAnyRole_returnsOwnProfile() throws Exception {
        var student = userWithRole("student.forme", "STUDENT");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearerToken(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(student.getUsername()))
                .andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.roleCodes[0]").value("STUDENT"));
    }

    @Test
    void changeOwnPassword_UC45_deniedWithoutJwt_returns403() throws Exception {
        mockMvc.perform(put("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeOwnPasswordRequest("old", "MatKhauMoi@9"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeOwnPassword_UC45_allowedForAnyRole_returns204() throws Exception {
        var teacher = userWithRoleWithPassword("teacher.selfchange", "TEACHER", "MatKhauCu@8");

        mockMvc.perform(put("/api/auth/me/password")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeOwnPasswordRequest("MatKhauCu@8", "MatKhauMoi@9"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void changeOwnPassword_UC45_A1_wrongCurrentPassword_returns401() throws Exception {
        var teacher = userWithRoleWithPassword("teacher.wrongcurrent", "TEACHER", "MatKhauCu@8");

        mockMvc.perform(put("/api/auth/me/password")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeOwnPasswordRequest("SaiMatKhau@1", "MatKhauMoi@9"))))
                .andExpect(status().isUnauthorized());
    }
}
