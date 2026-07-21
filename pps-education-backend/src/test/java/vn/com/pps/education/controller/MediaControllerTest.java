package vn.com.pps.education.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.support.AbstractControllerTest;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: API upload file audio/
 * ảnh cho UC-40 (Question.audioUrl/imageUrl). Chỉ cần đăng nhập, không có
 * @PreAuthorize riêng — test này khoá lại đúng hành vi đó qua HTTP thật.
 */
@Transactional
class MediaControllerTest extends AbstractControllerTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void mediaStorageDir(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage-dir", () -> storageDir.toString());
    }

    @Test
    void upload_MainFlow_authenticatedUserGetsPubliclyServableUrl() throws Exception {
        User teacher = userWithRole("teacher.media", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-audio".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(endsWith(".mp3")))
                .andExpect(jsonPath("$.url").value(containsString("/media-files/")));
    }

    @Test
    void upload_A_deniedWithoutJwt_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-audio".getBytes());

        mockMvc.perform(multipart("/api/media/upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_A_rejectsUnsupportedFileType_returns400() throws Exception {
        User teacher = userWithRole("teacher.media.bad", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isBadRequest());
    }
}
