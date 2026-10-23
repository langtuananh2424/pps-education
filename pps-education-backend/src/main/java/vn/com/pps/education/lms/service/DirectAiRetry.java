package vn.com.pps.education.lms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

/**
 * Bổ sung 2026-10-23 (đã xác nhận với người dùng) — thử lại lỗi TẠM THỜI khi gọi thẳng Claude/Groq/Azure
 * ({@link ClaudeAiClient}, {@link GroqSpeechClient}, {@link AzurePronunciationClient}) trước khi
 * {@link AiGradingFallbackService} rơi về 9Router: tối đa {@code maxAttempts} lần (mặc định 3), nghỉ 1s rồi 2s.
 *
 * Chỉ thử lại: lỗi kết nối (IOException KHÔNG phải timeout) và HTTP 408/429/5xx (kể cả 529 "overloaded").
 * KHÔNG thử lại: timeout (đã chờ tới hạn, thử lại sẽ vượt hạn nginx 180s), 4xx khác (sai key, hết tiền, request
 * sai — thử lại vô ích) — trả luôn để caller rơi về 9Router.
 */
final class DirectAiRetry {

    private static final Logger log = LoggerFactory.getLogger(DirectAiRetry.class);

    @FunctionalInterface
    interface Sender {
        HttpResponse<String> send() throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private DirectAiRetry() {
    }

    static HttpResponse<String> send(String label, int maxAttempts, Sender sender) throws IOException, InterruptedException {
        return send(label, maxAttempts, sender, Thread::sleep);
    }

    static HttpResponse<String> send(String label, int maxAttempts, Sender sender, Sleeper sleeper)
            throws IOException, InterruptedException {
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = sender.send();
            } catch (HttpTimeoutException e) {
                throw e;
            } catch (IOException e) {
                if (attempt >= attempts) {
                    throw e;
                }
                log.warn("{}: lỗi kết nối (lần {}/{}), thử lại. {}", label, attempt, attempts, e.getMessage());
                sleeper.sleep(1000L * attempt);
                continue;
            }
            if (!isTransient(response.statusCode()) || attempt >= attempts) {
                return response;
            }
            log.warn("{}: HTTP {} (lần {}/{}), thử lại.", label, response.statusCode(), attempt, attempts);
            sleeper.sleep(1000L * attempt);
        }
    }

    static boolean isTransient(int status) {
        return status == 408 || status == 429 || status >= 500;
    }
}
