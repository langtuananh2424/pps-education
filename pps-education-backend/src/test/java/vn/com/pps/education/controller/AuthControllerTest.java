package vn.com.pps.education.controller;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/auth/me: yêu cầu JWT hợp lệ (SecurityConfig — rule cụ thể hơn
 * "/api/auth/**" permitAll), nhưng KHÔNG cần permission code cụ thể — bất
 * kỳ role nào cũng gọi được (đọc hồ sơ chính mình).
 */
@Transactional
class AuthControllerTest extends AbstractControllerTest {

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
}
