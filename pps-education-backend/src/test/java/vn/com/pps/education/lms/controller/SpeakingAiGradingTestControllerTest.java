package vn.com.pps.education.lms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rà soát bảo mật 2026-09-28 — endpoint spike chấm AI Speaking trước đây mở cho MỌI tài khoản đã đăng
 * nhập (tốn chi phí AI + lưu file). Giờ chỉ quyền system.settings.manage mới gọi được.
 */
@Transactional
class SpeakingAiGradingTestControllerTest extends AbstractControllerTest {

    @Test
    void student_isForbidden() throws Exception {
        User student = userWithRole("student.devtools", "STUDENT");
        MockMultipartFile audio = new MockMultipartFile("audio", "a.webm", "audio/webm", "x".getBytes());

        mockMvc.perform(multipart("/api/dev-tools/speaking-grading-test")
                        .file(audio)
                        .header("Authorization", bearerToken(student, "STUDENT")))
                .andExpect(status().isForbidden());
    }
}
