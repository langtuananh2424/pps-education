package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.support.AbstractControllerTest;

import java.time.LocalDate;

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

    @Autowired
    private StudentRepository studentRepository;

    @Test
    void me_deniedWithoutJwt_returns401() throws Exception {
        // JwtAuthenticationEntryPoint (SecurityConfig) phân biệt 401 (chưa xác thực) với
        // 403 (đã xác thực nhưng thiếu quyền, xem GlobalExceptionHandler) - FE dựa vào 401
        // này để tự động refresh token thay vì bắt người dùng đăng xuất/đăng nhập lại thủ công.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_deniedWithExpiredOrInvalidJwt_returns401() throws Exception {
        // JwtAuthenticationFilter chỉ clearContext() khi token hết hạn/không hợp lệ rồi cho
        // đi tiếp (không tự trả lỗi) -> request tới authorizeHttpRequests với SecurityContext
        // rỗng, phải trả 401 giống hệt trường hợp không gửi JWT, không phải 403.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer not-a-valid-jwt-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_allowedForAnyRole_returnsOwnProfile() throws Exception {
        var student = userWithRole("student.forme", "STUDENT");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearerToken(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(student.getUsername()))
                .andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.roleCodes[0]").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").doesNotExist()); // chưa có hồ sơ Student liên kết
    }

    /**
     * UC-42 tiền đề: tài khoản Học sinh tự đăng nhập tra ra studentId của chính
     * mình qua GET /api/auth/me (tương tự GET /api/portal/parent/children cho
     * Phụ huynh) để gọi tiếp các API Portal cần studentId.
     */
    @Test
    void me_UC42_returnsStudentIdWhenAccountLinkedToStudentProfile() throws Exception {
        User student = userWithRole("student.withprofile", "STUDENT");
        Student studentProfile = new Student();
        studentProfile.setUser(student);
        studentProfile.setStudentCode("HS-AUTHCTRL-TEST-1");
        studentProfile.setDateOfBirth(LocalDate.of(2012, 5, 1));
        studentProfile.setEnrollmentDate(LocalDate.now());
        studentProfile = studentRepository.save(studentProfile);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearerToken(student, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentProfile.getId()));
    }

    @Test
    void changeOwnPassword_UC45_deniedWithoutJwt_returns401() throws Exception {
        mockMvc.perform(put("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeOwnPasswordRequest("old", "MatKhauMoi@9"))))
                .andExpect(status().isUnauthorized());
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
