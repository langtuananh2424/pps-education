package vn.com.pps.education.media.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import vn.com.pps.education.config.SignedMediaUrlJacksonConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng) - file cá nhân chỉ đọc được qua URL có chữ
 * ký, có hạn (MediaUrlSigner + SignedMediaUrlJacksonConfig). Unit test thuần, không chạm mạng: ký URL
 * S3 là phép tính HMAC cục bộ.
 */
class MediaUrlSignerTest {

    private static final String PUBLIC_BASE_URL = "https://files.pps.test";
    private static final String BUCKET = "pps-media";

    private final MediaUrlSigner signer = new MediaUrlSigner(
            PUBLIC_BASE_URL, BUCKET, "access", "secret", PUBLIC_BASE_URL, 60);

    @Test
    void sign_privateFile_returnsExpiringSignedUrlOnPublicHost() {
        String url = PUBLIC_BASE_URL + "/profiles/students/images/abc.png";

        String signed = signer.sign(url);

        assertThat(signed)
                .startsWith(PUBLIC_BASE_URL + "/pps-media/profiles/students/images/abc.png?")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=3600");
    }

    @Test
    void sign_allPrivateModulesSigned() {
        for (String key : List.of("profiles/parents/images/a.png", "profiles/employees/images/a.png",
                "lms/review-video-submissions/audio/a.webm", "lms/exercise-answer-submissions/audio/a.webm",
                "academic/report-templates/generated/a.docx", "academic/report-templates/documents/a.pdf")) {
            assertThat(signer.sign(PUBLIC_BASE_URL + "/" + key)).as(key).contains("X-Amz-Signature=");
        }
    }

    /** Nội dung giảng dạy vẫn công khai, URL ngoài hệ thống và văn bản thường giữ nguyên. */
    @Test
    void sign_nonPrivateValues_unchanged() {
        for (String value : List.of(PUBLIC_BASE_URL + "/lms/questions/audio/a.mp3",
                PUBLIC_BASE_URL + "/lms/review-videos/video/a.mp4",
                "https://www.youtube.com/watch?v=x", "Nguyễn Văn A", PUBLIC_BASE_URL + "-evil.com/profiles/students/x")) {
            assertThat(signer.sign(value)).isEqualTo(value);
        }
        assertThat(signer.sign(null)).isNull();
    }

    @Test
    void toCanonical_roundTripsSignedUrlBackToStoredForm() {
        String url = PUBLIC_BASE_URL + "/lms/exercise-answer-submissions/audio/abc-123.webm";

        assertThat(signer.toCanonical(signer.sign(url))).isEqualTo(url);
        assertThat(signer.toCanonical(url)).isEqualTo(url);
        assertThat(signer.toCanonical("https://evil.com/pps-media/x?X-Amz-Signature=1")).isEqualTo("https://evil.com/pps-media/x?X-Amz-Signature=1");
    }

    @Test
    void disabledWhenEndpointBlank_returnsUrlsUnchanged() {
        MediaUrlSigner disabled = new MediaUrlSigner(PUBLIC_BASE_URL, BUCKET, "a", "s", "", 60);
        String url = PUBLIC_BASE_URL + "/profiles/students/images/abc.png";

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.sign(url)).isEqualTo(url);
    }

    record ProfileDto(String fullName, String portraitUrl, List<String> audioUrls) {
    }

    /** Tầng JSON HTTP: response tự ký mọi URL file cá nhân (kể cả trong List/Map), request đổi ngược về URL gốc. */
    @Test
    void jacksonConverter_signsResponsesAndCanonicalizesRequests() throws Exception {
        List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(new MappingJackson2HttpMessageConverter()));
        new SignedMediaUrlJacksonConfig(signer).extendMessageConverters(converters);
        ObjectMapper mapper = ((MappingJackson2HttpMessageConverter) converters.get(0)).getObjectMapper();
        String portrait = PUBLIC_BASE_URL + "/profiles/students/images/p.png";
        String audio = PUBLIC_BASE_URL + "/lms/review-video-submissions/audio/a.webm";

        String json = mapper.writeValueAsString(new ProfileDto("Nguyễn Văn A", portrait, List.of(audio)));
        String mapJson = mapper.writeValueAsString(Map.of("fileUrl", audio));

        assertThat(json).doesNotContain("\"" + portrait + "\"").contains("X-Amz-Signature").contains("Nguyễn Văn A");
        assertThat(mapJson).contains("X-Amz-Signature");
        ProfileDto back = mapper.readValue(json, ProfileDto.class);
        assertThat(back.portraitUrl()).isEqualTo(portrait);
        assertThat(back.audioUrls()).containsExactly(audio);
    }

    /** ObjectMapper dùng chung (lưu cột JSON, gọi AI...) KHÔNG bị ảnh hưởng. */
    @Test
    void jacksonConverter_doesNotTouchSharedObjectMapper() throws Exception {
        ObjectMapper shared = new ObjectMapper();
        List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(new MappingJackson2HttpMessageConverter(shared)));
        new SignedMediaUrlJacksonConfig(signer).extendMessageConverters(converters);
        String portrait = PUBLIC_BASE_URL + "/profiles/students/images/p.png";

        assertThat(shared.writeValueAsString(portrait)).isEqualTo("\"" + portrait + "\"");
    }
}
