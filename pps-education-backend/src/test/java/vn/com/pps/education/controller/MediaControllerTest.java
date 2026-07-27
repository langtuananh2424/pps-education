package vn.com.pps.education.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: API upload file audio/
 * ảnh cho UC-40 (Question.audioUrl/imageUrl). Chỉ cần đăng nhập, không có
 * @PreAuthorize riêng — test này khoá lại đúng hành vi đó qua HTTP thật.
 * S3Client (Cloudflare R2) bị mock để không gọi mạng thật trong CI.
 */
@Transactional
class MediaControllerTest extends AbstractControllerTest {

    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";

    @MockBean
    private S3Client r2Client;

    @DynamicPropertySource
    static void mediaR2Config(DynamicPropertyRegistry registry) {
        registry.add("app.media.r2.bucket", () -> "test-bucket");
        registry.add("app.media.r2.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @Test
    void upload_MainFlow_authenticatedUserGetsPubliclyServableUrl() throws Exception {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        User teacher = userWithRole("teacher.media", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-audio".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("module", "LMS_QUESTION")
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith(PUBLIC_BASE_URL + "/lms/questions/audio/")))
                .andExpect(jsonPath("$.url").value(endsWith(".mp3")));
    }

    @Test
    void upload_A_deniedWithoutJwt_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-audio".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("module", "LMS_QUESTION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_A_rejectsUnsupportedFileType_returns400() throws Exception {
        User teacher = userWithRole("teacher.media.bad", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("module", "LMS_QUESTION")
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_boSung_rejectsUnknownModule_returns400() throws Exception {
        User teacher = userWithRole("teacher.media.mod", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-audio".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("module", "KHONG_TON_TAI")
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22, theo yêu
     * cầu FE): CURRICULUM_DOCUMENT nhận thêm PDF/Word/Excel/video - xem
     * MediaModule.acceptsOfficeDocuments()/acceptsVideo()/MediaStorageService.
     */
    @Test
    void upload_boSung_curriculumDocumentAcceptsPdf_returns200() throws Exception {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        User staff = userWithRole("staff.media.doc", "HEAD_ACADEMIC");
        MockMultipartFile file = new MockMultipartFile("file", "tai-lieu.pdf", "application/pdf", "fake-pdf".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("module", "CURRICULUM_DOCUMENT")
                        .header("Authorization", bearerToken(staff, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith(PUBLIC_BASE_URL + "/lms/curriculum-documents/documents/")))
                .andExpect(jsonPath("$.url").value(endsWith(".pdf")));
    }

    @Test
    void upload_boSung_lmsQuestionAcceptsPdf_returns200() throws Exception {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        User teacher = userWithRole("teacher.media.pdf", "TEACHER");
        MockMultipartFile file = new MockMultipartFile("file", "tai-lieu.pdf", "application/pdf", "fake-pdf".getBytes());

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("module", "LMS_QUESTION")
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith(PUBLIC_BASE_URL + "/lms/questions/documents/")))
                .andExpect(jsonPath("$.url").value(endsWith(".pdf")));
    }
}
