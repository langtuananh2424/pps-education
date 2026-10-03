package vn.com.pps.education.media.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import vn.com.pps.education.exception.MediaModuleNotAllowedException;
import vn.com.pps.education.permission.repository.UserRoleRepository;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    private static final byte[] PDF_BYTES = "%PDF-1.7 fake".getBytes();

    private final S3Client r2Client = mock(S3Client.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final MediaStorageService service = new MediaStorageService(r2Client, userRoleRepository, BUCKET, PUBLIC_BASE_URL);

    @BeforeEach
    void stubPutObject() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    @Test
    void store_MainFlow_uploadsImageAndReturnsPublicUrlPreservingExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.PNG", "image/png", PNG_BYTES);

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
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd", "image/png", PNG_BYTES);

        String url = service.store(file, MODULE);

        assertThat(url).doesNotContain("..").startsWith(PUBLIC_BASE_URL + "/lms/questions/images/");
    }

    @Test
    void store_boSung_rejectsUnknownModule() {
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.png", "image/png", PNG_BYTES);

        assertThatThrownBy(() -> service.store(file, "KHONG_TON_TAI")).isInstanceOf(IllegalArgumentException.class);
        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22, theo yêu
     * cầu FE) — test hồi quy sau khi tách acceptsDocuments thành
     * acceptsVideo/acceptsOfficeDocuments (2026-07-27, xem MediaModule):
     * CURRICULUM_DOCUMENT vẫn nhận PDF y hệt trước khi tách cờ.
     */
    @Test
    void store_boSung_curriculumDocumentAcceptsPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "tai-lieu.pdf", "application/pdf", PDF_BYTES);

        String url = service.store(file, "CURRICULUM_DOCUMENT");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/curriculum-documents/documents/").endsWith(".pdf");
    }

    /** Test hồi quy: CURRICULUM_DOCUMENT vẫn nhận video y hệt trước khi tách cờ. */
    @Test
    void store_regression_curriculumDocumentStillAcceptsVideo() {
        MockMultipartFile file = new MockMultipartFile("file", "gioi-thieu.mp4", "video/mp4", "fake-mp4-bytes".getBytes());

        String url = service.store(file, "CURRICULUM_DOCUMENT");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/curriculum-documents/video/").endsWith(".mp4");
    }

    /**
     * UC-23: Kho Video Ôn tập (đổi tên từ LESSON_MATERIAL/Kho bài giảng,
     * 2026-07-27) — chỉ còn nhận video/audio, KHÔNG còn nhận Word/Excel/PDF
     * (đã xác nhận với người dùng: bỏ hẳn hỗ trợ tài liệu văn phòng).
     */
    @Test
    void store_UC23_A_reviewVideoRejectsWordAndExcel() {
        MockMultipartFile word = new MockMultipartFile("file", "de-cuong.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "fake-docx".getBytes());
        MockMultipartFile excel = new MockMultipartFile("file", "bang-diem.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "fake-xlsx".getBytes());

        assertThatThrownBy(() -> service.store(word, "REVIEW_VIDEO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store(excel, "REVIEW_VIDEO")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_UC23_MainFlow_reviewVideoAcceptsVideo() {
        MockMultipartFile file = new MockMultipartFile("file", "video-tu-ket-noi.mp4", "video/mp4", "fake-mp4-bytes".getBytes());

        String url = service.store(file, "REVIEW_VIDEO");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/review-videos/video/").endsWith(".mp4");
    }

    @Test
    void store_UC23_MainFlow_reviewVideoAcceptsAudio() {
        MockMultipartFile file = new MockMultipartFile("file", "video-phan-xa.mp3", "audio/mpeg", "fake-mp3-bytes".getBytes());

        String url = service.store(file, "REVIEW_VIDEO");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/review-videos/audio/").endsWith(".mp3");
    }

    @Test
    void store_boSung_rejectsDocumentExceeding20MB() {
        byte[] tooLarge = new byte[21 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", tooLarge);

        assertThatThrownBy(() -> service.store(file, "CURRICULUM_DOCUMENT")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22, theo yêu
     * cầu FE) — test hồi quy sau khi tách cờ (2026-07-27): LMS_QUESTION vẫn
     * bật acceptsOfficeDocuments=true để câu tự luận (Question.imageUrl,
     * UC-40) nhận file PDF y hệt trước.
     */
    @Test
    void store_boSung_lmsQuestionAcceptsPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.pdf", "application/pdf", PDF_BYTES);

        String url = service.store(file, MODULE);

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/questions/documents/").endsWith(".pdf");
    }

    /** Test hồi quy: LMS_QUESTION vẫn nhận video y hệt trước khi tách cờ. */
    @Test
    void store_regression_lmsQuestionStillAcceptsVideo() {
        MockMultipartFile file = new MockMultipartFile("file", "huong-dan.mp4", "video/mp4", "fake-mp4-bytes".getBytes());

        String url = service.store(file, MODULE);

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/questions/video/").endsWith(".mp4");
    }

    // ===================== Siết upload (rà soát bảo mật 2026-09-28) =====================

    @Test
    void store_security_rejectsSvgImage() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes());

        assertThatThrownBy(() -> service.store(file, MODULE)).isInstanceOf(IllegalArgumentException.class);
        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_security_rejectsHtmlOutsideReportTemplate() {
        MockMultipartFile file = new MockMultipartFile("file", "trang.html", "text/html", "<script>x</script>".getBytes());

        assertThatThrownBy(() -> service.store(file, MODULE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store(file, "CURRICULUM_DOCUMENT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_regression_reportTemplateStillAcceptsHtml() {
        MockMultipartFile file = new MockMultipartFile("file", "mau.html", "text/html; charset=UTF-8", "<p>{{ten}}</p>".getBytes());

        String url = service.store(file, "REPORT_TEMPLATE");

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/academic/report-templates/documents/").endsWith(".html");
    }

    /** Content-Type do client tự khai - file HTML đổi tên thành .png/.pdf phải bị chặn theo magic bytes. */
    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/webp", "application/pdf"})
    void store_security_rejectsContentNotMatchingDeclaredType(String declaredType) {
        MockMultipartFile file = new MockMultipartFile("file", "anh.png", declaredType, "<html><script>x</script></html>".getBytes());

        assertThatThrownBy(() -> service.store(file, MODULE)).isInstanceOf(IllegalArgumentException.class);
        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void storeUpload_security_studentCannotUploadToStaffModule() {
        givenRoles(7L, "STUDENT");
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);

        assertThatThrownBy(() -> service.storeUpload(file, "LMS_QUESTION", 7L))
                .isInstanceOf(MediaModuleNotAllowedException.class);
        assertThatThrownBy(() -> service.storeUpload(file, "EMPLOYEE", 7L))
                .isInstanceOf(MediaModuleNotAllowedException.class);
        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void storeUpload_MainFlow_studentUploadsOwnSubmissionAndPortrait() {
        givenRoles(7L, "STUDENT");

        assertThat(service.storeUpload(new MockMultipartFile("file", "bai.webm", "audio/webm", "x".getBytes()),
                "EXERCISE_ANSWER_SUBMISSION", 7L)).startsWith(PUBLIC_BASE_URL + "/lms/exercise-answer-submissions/audio/");
        assertThat(service.storeUpload(new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES),
                "STUDENT", 7L)).startsWith(PUBLIC_BASE_URL + "/profiles/students/images/");
    }

    @Test
    void storeUpload_MainFlow_staffUploadsTeachingContent() {
        givenRoles(8L, "TEACHER");

        String url = service.storeUpload(new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES), "LMS_QUESTION", 8L);

        assertThat(url).startsWith(PUBLIC_BASE_URL + "/lms/questions/images/");
    }

    /** REPORT_TEMPLATE chỉ nhận qua UC-67 (ReportTemplateService tự phân quyền), kể cả sysadmin cũng không qua API chung. */
    @Test
    void storeUpload_security_reportTemplateNeverThroughGenericApi() {
        givenRoles(9L, "SYS_ADMIN");
        MockMultipartFile file = new MockMultipartFile("file", "mau.html", "text/html", "<p>x</p>".getBytes());

        assertThatThrownBy(() -> service.storeUpload(file, "REPORT_TEMPLATE", 9L))
                .isInstanceOf(MediaModuleNotAllowedException.class);
    }

    private void givenRoles(Long userId, String... roleCodes) {
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(roleCodes));
    }

    // ===================== Chống SSRF (rà soát bảo mật 2026-09-28) =====================

    @Test
    void download_ownUrl_readsObjectByKeyFromStorage() {
        GetObjectResponse meta = GetObjectResponse.builder().contentType("audio/mp4").build();
        when(r2Client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(meta, new ByteArrayInputStream("audio-bytes".getBytes())));

        MediaStorageService.DownloadedFile file =
                service.downloadWithContentType(PUBLIC_BASE_URL + "/review-videos/audio/abc.m4a");

        assertThat(file.bytes()).isEqualTo("audio-bytes".getBytes());
        assertThat(file.contentType()).isEqualTo("audio/mp4");
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(r2Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("review-videos/audio/abc.m4a");
    }

    /** URL không do hệ thống sinh ra -> từ chối, server KHÔNG được mở kết nối tới bất kỳ đâu. */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://minio:9000/pps-media/x.webm",
            "http://127.0.0.1:8080/actuator/health",
            "file:///etc/hostname",
            "https://media.pps.edu.vn.evil.com/x.webm",
            "https://media.pps.edu.vn@evil.com/x.webm",
            "https://media.pps.edu.vn",
            "https://media.pps.edu.vn/",
            "https://media.pps.edu.vn/../secret",
            "https://media.pps.edu.vn/a/../../secret",
            "https://media.pps.edu.vn//x.webm",
            "https://media.pps.edu.vn/x.webm?redirect=http://127.0.0.1",
            " "
    })
    void download_foreignOrMalformedUrl_rejectedWithoutTouchingStorage(String url) {
        assertThatThrownBy(() -> service.downloadWithContentType(url))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.requireStoredUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(r2Client);
    }

    @Test
    void download_nullUrl_rejected() {
        assertThatThrownBy(() -> service.downloadWithContentType(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Trước đây lỗi storage bị nuốt rồi trả HTML mẫu giả lập - giờ phải báo lỗi thật cho caller. */
    @Test
    void download_storageError_propagatesInsteadOfFakeContent() {
        when(r2Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> service.downloadWithContentType(PUBLIC_BASE_URL + "/lms/x.docx"))
                .isInstanceOf(software.amazon.awssdk.services.s3.model.NoSuchKeyException.class);
    }
}
