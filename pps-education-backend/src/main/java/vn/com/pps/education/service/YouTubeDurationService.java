package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-73 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-14) —
 * dò thời lượng (giây) của 1 video YouTube qua YouTube Data API v3
 * ({@code videos.list?part=contentDetails}), dùng cho import Excel hàng
 * loạt "bộ" video ôn tập (Kho Video Ôn tập, UC-23) — KHÁC hẳn cơ chế dò
 * thời lượng lúc tạo tay 1 video ở LecturesPage.tsx (chạy hoàn toàn phía
 * trình duyệt qua YouTube IFrame Player API), backend xử lý file Excel
 * upload không có trình duyệt để chạy JS nên phải gọi API thật có key
 * riêng. Xem docs/uc/phan-he-07-lms-portal.md UC-73.
 */
@Service
public class YouTubeDurationService {

    private static final Pattern ISO8601_DURATION =
            Pattern.compile("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$");
    /** Chấp nhận watch?v=, youtu.be/, shorts/, embed/ — mirror extractYouTubeVideoId (LecturesPage.tsx). */
    private static final Pattern VIDEO_ID_PATTERN =
            Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Value("${app.youtube.api-key:}")
    private String apiKey;

    public YouTubeDurationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Trích videoId từ mọi dạng URL YouTube phổ biến — trả {@code null} nếu link không hợp lệ. */
    public String extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Gọi YouTube Data API v3 lấy thời lượng (giây). Ném {@link IllegalStateException} nếu chưa cấu
     * hình {@code app.youtube.api-key}, video không tồn tại/riêng tư (contentDetails rỗng), hoặc API
     * lỗi (quota hết, key sai...) — caller (import Excel) bắt lỗi này thành lỗi riêng của DÒNG đó,
     * không chặn các dòng khác trong file (xem ReviewVideoCatalogImportService).
     */
    public int getDurationSeconds(String videoId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Chưa cấu hình YOUTUBE_API_KEY — không tự dò được thời lượng video YouTube.");
        }
        try {
            String encodedId = URLEncoder.encode(videoId, StandardCharsets.UTF_8);
            URI uri = URI.create("https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id="
                    + encodedId + "&key=" + apiKey);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Gọi YouTube Data API lỗi (HTTP " + response.statusCode() + "): " + response.body());
            }
            JsonNode items = objectMapper.readTree(response.body()).path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new IllegalStateException(
                        "Không tìm thấy video YouTube id=" + videoId + " (đã xóa, riêng tư, hoặc sai id).");
            }
            String isoDuration = items.get(0).path("contentDetails").path("duration").asText(null);
            Integer seconds = parseIso8601DurationSeconds(isoDuration);
            if (seconds == null || seconds <= 0) {
                throw new IllegalStateException("Không đọc được thời lượng video YouTube id=" + videoId + ".");
            }
            return seconds;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Gọi YouTube Data API thất bại: " + e.getMessage(), e);
        }
    }

    private Integer parseIso8601DurationSeconds(String isoDuration) {
        if (isoDuration == null || isoDuration.isBlank()) {
            return null;
        }
        Matcher matcher = ISO8601_DURATION.matcher(isoDuration.trim());
        if (!matcher.matches()) {
            return null;
        }
        int hours = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
        int minutes = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        int seconds = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return hours * 3600 + minutes * 60 + seconds;
    }
}
