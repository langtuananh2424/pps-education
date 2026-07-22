package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: API upload file audio/
 * ảnh cho Question.audioUrl/imageUrl (UC-40), lưu trên Cloudflare R2. S3Client
 * bị mock để không gọi mạng thật trong unit test (đúng ngoại lệ trong
 * .claude/rules/testing.md - test Service logic thuần không chạm DB/mạng
 * ngoài không cần Testcontainers).
 */
class MediaStorageServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String MODULE = "LMS_QUESTION";

    private final S3Client r2Client = mock(S3Client.class);
    private final MediaStorageService service = new MediaStorageService(r2Client, BUCKET, PUBLIC_BASE_URL);

    @BeforeEach
    void stubPutObject() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    @Test
    void store_MainFlow_uploadsImageAndReturnsPublicUrlPreservingExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.PNG", "image/png", "fake-png-bytes".getBytes());

        String url = service.store(file, MODULE);

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/questions/images/").endsWith(".png");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).startsWith("lms/questions/images/");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void store_MainFlow_uploadsAudioFile() {
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-mp3-bytes".getBytes());

        String url = service.store(file, MODULE);

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/questions/audio/").endsWith(".mp3");
    }

    @Test
    void store_A_rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service.store(file, MODULE)).isInstanceOf(IllegalArgumentException.class);
        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_A_rejectsImageExceeding10MB() {
        byte[] tooLarge = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

        assertThatThrownBy(() -> service.store(file, MODULE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_A_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(file, MODULE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_boSung_sanitizesUnsafeExtensionFromOriginalFilename() {
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd", "image/png", "x".getBytes());

        String url = service.store(file, MODULE);

        assertThat(url).doesNotContain("..").startsWith(PUBLIC_BASE_URL + "/lms/questions/images/");
    }

    @Test
    void store_boSung_rejectsUnknownModule() {
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.png", "image/png", "x".getBytes());

        assertThatThrownBy(() -> service.store(file, "KHONG_TON_TAI")).isInstanceOf(IllegalArgumentException.class);
        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22, theo yêu
     * cầu FE): CURRICULUM_DOCUMENT/LESSON_MATERIAL nhận thêm PDF/Word/Excel/
     * video ngoài audio/ảnh - xem MediaModule.acceptsDocuments().
     */
    @Test
    void store_boSung_curriculumDocumentAcceptsPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "tai-lieu.pdf", "application/pdf", "fake-pdf-bytes".getBytes());

        String url = service.store(file, "CURRICULUM_DOCUMENT");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/curriculum-documents/documents/").endsWith(".pdf");
    }

    @Test
    void store_boSung_lessonMaterialAcceptsWordAndExcel() {
        MockMultipartFile word = new MockMultipartFile("file", "de-cuong.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "fake-docx".getBytes());
        MockMultipartFile excel = new MockMultipartFile("file", "bang-diem.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "fake-xlsx".getBytes());

        assertThat(service.store(word, "LESSON_MATERIAL"))
                .startsWith(PUBLIC_BASE_URL + "/lms/lesson-materials/documents/").endsWith(".docx");
        assertThat(service.store(excel, "LESSON_MATERIAL"))
                .startsWith(PUBLIC_BASE_URL + "/lms/lesson-materials/documents/").endsWith(".xlsx");
    }

    @Test
    void store_boSung_lessonMaterialAcceptsVideo() {
        MockMultipartFile file = new MockMultipartFile("file", "bai-giang.mp4", "video/mp4", "fake-mp4-bytes".getBytes());

        String url = service.store(file, "LESSON_MATERIAL");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/lesson-materials/video/").endsWith(".mp4");
    }

    @Test
    void store_boSung_rejectsDocumentExceeding20MB() {
        byte[] tooLarge = new byte[21 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", tooLarge);

        assertThatThrownBy(() -> service.store(file, "CURRICULUM_DOCUMENT")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22, theo yêu
     * cầu FE): LMS_QUESTION cũng bật acceptsDocuments=true để câu tự luận
     * (Question.imageUrl, UC-40) nhận file PDF - xem MediaModule.
     */
    @Test
    void store_boSung_lmsQuestionAcceptsPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.pdf", "application/pdf", "fake-pdf-bytes".getBytes());

        String url = service.store(file, MODULE);

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/questions/documents/").endsWith(".pdf");
    }
}
