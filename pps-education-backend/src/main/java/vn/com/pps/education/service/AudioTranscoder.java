package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — chuyển bản ghi trình duyệt (webm/opus
 * trên Chrome/Android, mp4 trên Safari/iOS) sang WAV PCM16 mono 16 kHz bằng ffmpeg, vì công cụ đo tiếng
 * nói của người training ({@code SpeechMeter}) chỉ đọc được WAV và toàn bộ hiệu chuẩn của họ chạy trên
 * WAV. Dùng file tạm (không pipe) vì WAV ghi ra pipe không có kích thước chunk hợp lệ.
 *
 * ffmpeg KHÔNG có sẵn trong image backend trước đây — đã thêm vào Dockerfile cùng đợt này. Nếu chạy máy
 * dev không cài ffmpeg (hoặc convert lỗi/quá hạn), trả {@link Optional#empty()} và caller phải ghi log +
 * chạy ở chế độ suy giảm (không có số đo tiếng nói): bộ chặn transcript bịa KHÔNG chạy — không phải "đạt".
 */
@Component
public class AudioTranscoder {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscoder.class);

    @Value("${app.ai-grading.reflex-v2.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ai-grading.reflex-v2.ffmpeg-timeout-seconds:20}")
    private int timeoutSeconds;

    /** @return WAV PCM16 mono 16 kHz, hoặc rỗng nếu đầu vào đã là WAV không cần đổi / không chuyển được. */
    public Optional<byte[]> toWav(byte[] input, String mimeType) {
        if (input == null || input.length == 0) {
            return Optional.empty();
        }
        Path in = null;
        Path out = null;
        try {
            in = Files.createTempFile("reflex-in-", ".bin");
            out = Files.createTempFile("reflex-out-", ".wav");
            Files.write(in, input);
            Process process = new ProcessBuilder(List.of(ffmpegPath, "-hide_banner", "-loglevel", "error", "-y",
                    "-i", in.toString(), "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "-f", "wav", out.toString()))
                    .redirectErrorStream(true)
                    .start();
            // Chờ có hạn TRƯỚC khi đọc log: đọc trước sẽ treo vô hạn nếu ffmpeg bị kẹt. -loglevel error nên
            // log rất nhỏ, không đầy bộ đệm pipe.
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("AudioTranscoder: ffmpeg quá {}s, bỏ qua (mime={}).", timeoutSeconds, mimeType);
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                log.warn("AudioTranscoder: ffmpeg lỗi (mime={}): {}", mimeType, new String(process.getInputStream().readAllBytes()));
                return Optional.empty();
            }
            byte[] wav = Files.readAllBytes(out);
            return wav.length < 44 ? Optional.empty() : Optional.of(wav);
        } catch (IOException e) {
            log.warn("AudioTranscoder: không chạy được ffmpeg ('{}') — chế độ suy giảm, không đo được tiếng nói. {}", ffmpegPath, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            deleteQuietly(in);
            deleteQuietly(out);
        }
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // file tạm — hệ điều hành dọn sau
            }
        }
    }
}
