package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.pps.education.dto.ReflexPictureBriefResponse;
import vn.com.pps.education.dto.ReflexPictureCaptureResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b dạng tả tranh: hỗ trợ giáo viên soạn
 * mô tả tranh khi tạo câu hỏi. Luồng đã chốt: chụp khung hình trong video tại mốc câu hỏi → AI viết nháp 2-3 dòng →
 * giáo viên đối chiếu, sửa → lưu kèm câu hỏi ({@link ReflexQuestionFormatService#applyTo}). AI chỉ chạy 1 lần lúc
 * soạn, KHÔNG chạy ở mỗi bài nộp; lúc chấm chỉ mô tả CHỮ đã duyệt được gửi vào lượt chấm.
 *
 * Chống SSRF giống MediaStorageService: chỉ nhận URL file hệ thống đã lưu, đọc qua S3 client rồi ghi file tạm —
 * KHÔNG đưa URL cho ffmpeg. Video YouTube không chụp được (không tải được video) → FE yêu cầu giáo viên tải ảnh lên.
 */
@Service
public class ReflexPictureService {

    private static final Logger log = LoggerFactory.getLogger(ReflexPictureService.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    static final String BRIEF_SYSTEM_PROMPT = """
            Bạn giúp giáo viên tiếng Anh soạn MÔ TẢ BỨC TRANH cho bài "tả tranh" (PET Speaking Task 2) của học sinh THCS.
            Mô tả này chỉ dùng để giáo viên/AI chấm xét học sinh có tả đúng bức tranh hay lạc đề.

            Viết ĐÚNG 2 hoặc 3 dòng tiếng Việt, mỗi dòng một câu ngắn, theo thứ tự:
            1. Nơi chốn / bối cảnh (ở đâu, trong nhà hay ngoài trời).
            2. Có ai / bao nhiêu người hoặc con vật, đang làm gì.
            3. (Nếu có) chi tiết nổi bật dễ thấy: màu sắc, thời tiết, đồ vật lớn.

            Quy tắc:
            - Chỉ tả những gì NHÌN THẤY RÕ trong ảnh. Không đoán cảm xúc, tên người, địa danh, thương hiệu.
            - Không đánh số, không gạch đầu dòng, không markdown, không lời dẫn hay kết luận.
            - Nếu ảnh không có bức tranh/cảnh vật (chỉ là chữ, slide, màn hình đen, hình mờ không nhận ra) thì trả về
              đúng một dòng: KHÔNG THẤY TRANH
            """;
    static final String NO_PICTURE_MARKER = "KHÔNG THẤY TRANH";

    private final MediaStorageService mediaStorageService;
    private final NineRouterAiClient nineRouterAiClient;

    @Value("${app.ai-grading.reflex-v2.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ai-grading.reflex-v2.model:ag/gemini-3.6-flash-medium}")
    private String model;

    public ReflexPictureService(MediaStorageService mediaStorageService, NineRouterAiClient nineRouterAiClient) {
        this.mediaStorageService = mediaStorageService;
        this.nineRouterAiClient = nineRouterAiClient;
    }

    /**
     * Chụp 1 khung hình JPEG tại giây {@code timestampSeconds} của video đã tải lên hệ thống, lưu vào storage.
     *
     * @throws IllegalArgumentException (400) URL không phải file hệ thống, hoặc mốc vượt quá độ dài video / ffmpeg lỗi.
     */
    public ReflexPictureCaptureResponse captureFrame(String videoUrl, int timestampSeconds) {
        mediaStorageService.requireStoredUrl(videoUrl);
        if (timestampSeconds < 0) {
            throw new IllegalArgumentException("Mốc thời gian không hợp lệ.");
        }
        Path video = null;
        Path frame = null;
        try {
            video = Files.createTempFile("reflex-video-", ".bin");
            frame = Files.createTempFile("reflex-frame-", ".jpg");
            mediaStorageService.downloadToFile(videoUrl, video);
            // -ss trước -i: tua nhanh theo keyframe rồi giải mã chính xác tới đúng giây; thu nhỏ còn tối đa 1280px
            // ngang (đủ để giáo viên đối chiếu, nhẹ khi gửi AI).
            Process process = new ProcessBuilder(List.of(ffmpegPath, "-hide_banner", "-loglevel", "error", "-y",
                    "-ss", String.valueOf(timestampSeconds), "-i", video.toString(), "-frames:v", "1",
                    "-vf", "scale='min(1280,iw)':-2", "-q:v", "3", frame.toString()))
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalArgumentException("Chụp khung hình quá lâu — hãy thử lại hoặc tải ảnh lên.");
            }
            byte[] jpeg = Files.readAllBytes(frame);
            if (process.exitValue() != 0 || jpeg.length == 0) {
                log.warn("ReflexPictureService: ffmpeg không chụp được giây {}: {}", timestampSeconds,
                        new String(process.getInputStream().readAllBytes()));
                throw new IllegalArgumentException("Không chụp được khung hình tại giây " + timestampSeconds
                        + " — mốc có thể vượt quá độ dài video. Hãy kiểm tra lại mốc hoặc tải ảnh lên.");
            }
            String imageUrl = mediaStorageService.storeGeneratedFile(jpeg, "frame.jpg", "image/jpeg", MediaModule.REVIEW_VIDEO);
            return new ReflexPictureCaptureResponse(imageUrl);
        } catch (IOException e) {
            log.warn("ReflexPictureService: không chạy được ffmpeg ('{}'). {}", ffmpegPath, e.getMessage());
            throw new IllegalArgumentException("Máy chủ chưa chụp được khung hình (thiếu ffmpeg) — hãy tải ảnh lên.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Chụp khung hình bị gián đoạn — hãy thử lại.");
        } finally {
            deleteQuietly(video);
            deleteQuietly(frame);
        }
    }

    /**
     * AI viết NHÁP mô tả tranh 2-3 dòng từ ảnh đã lưu. {@code brief} rỗng + {@code pictureFound=false} khi AI không
     * thấy tranh hoặc gọi AI lỗi — giáo viên tự gõ mô tả.
     */
    public ReflexPictureBriefResponse draftBrief(String imageUrl) {
        mediaStorageService.requireStoredUrl(imageUrl);
        MediaStorageService.DownloadedFile image = mediaStorageService.downloadWithContentType(imageUrl);
        String mime = image.contentType() == null ? "" : image.contentType().toLowerCase(Locale.ROOT);
        if (!mime.startsWith("image/")) {
            throw new IllegalArgumentException("File này không phải ảnh.");
        }
        if (image.bytes().length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Ảnh quá lớn (tối đa 10MB).");
        }
        NineRouterAiClient.AiTextResponse response = nineRouterAiClient.chatWithImage(BRIEF_SYSTEM_PROMPT,
                "Mô tả bức tranh trong ảnh này theo đúng quy tắc.", image.bytes(), mime, model);
        if (response == null) {
            log.warn("ReflexPictureService: AI không viết được mô tả tranh (imageUrl={}).", imageUrl);
            return new ReflexPictureBriefResponse("", false, false);
        }
        String brief = cleanBrief(response.content());
        boolean found = !brief.isEmpty() && !brief.toUpperCase(Locale.ROOT).contains(NO_PICTURE_MARKER);
        return new ReflexPictureBriefResponse(found ? brief : "", found, true);
    }

    /** Bỏ số thứ tự / gạch đầu dòng / dòng trống, giữ tối đa 3 dòng. */
    static String cleanBrief(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.lines()
                // Bỏ in đậm markdown TRƯỚC, không thì "**Trời nắng.**" bị coi dấu * đầu là gạch đầu dòng.
                .map(l -> l.replace("**", "").strip().replaceFirst("^(\\d+[.)]|[-•*])\\s*", ""))
                .filter(l -> !l.isEmpty())
                .limit(3)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // file tạm — hệ điều hành tự dọn
            }
        }
    }
}
