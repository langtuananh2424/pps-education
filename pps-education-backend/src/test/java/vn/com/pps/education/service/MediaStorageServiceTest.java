package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: API upload file audio/
 * ảnh cho Question.audioUrl/imageUrl (UC-40). Không chạm DB -> unit test
 * thuần, không cần Testcontainers (đúng ngoại lệ trong .claude/rules/testing.md).
 */
class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void store_MainFlow_savesImageFileAndReturnsUuidFilenamePreservingExtension() {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "de-thi.PNG", "image/png", "fake-png-bytes".getBytes());

        String filename = service.store(file);

        assertThat(filename).endsWith(".png");
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void store_MainFlow_savesAudioFile() {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "cau-hoi.mp3", "audio/mpeg", "fake-mp3-bytes".getBytes());

        String filename = service.store(file);

        assertThat(filename).endsWith(".mp3");
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void store_A_rejectsUnsupportedContentType() {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_A_rejectsImageExceeding10MB() {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        byte[] tooLarge = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_A_rejectsEmptyFile() {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_boSung_sanitizesUnsafeExtensionFromOriginalFilename() throws IOException {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd", "image/png", "x".getBytes());

        String filename = service.store(file);

        assertThat(filename).doesNotContain("/", "..");
        assertThat(tempDir.resolve(filename).getParent()).isEqualTo(tempDir.toAbsolutePath().normalize());
    }
}
