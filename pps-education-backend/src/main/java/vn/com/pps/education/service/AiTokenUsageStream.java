package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.Student;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — kênh SSE đẩy chi phí token
 * theo thời gian thực xuống trang Quản trị hệ thống → Sử dụng token AI, để quản trị viên thấy ngay mỗi
 * lượt chấm mà không phải tải lại trang.
 *
 * Chọn SSE (1 chiều server → trình duyệt) thay vì WebSocket vì luồng dữ liệu ở đây hoàn toàn 1 chiều —
 * trang chỉ nhận, không gửi gì lên; SSE chạy trên đúng HTTP sẵn có, tự kết nối lại khi rớt mạng, không
 * cần thêm hạ tầng. Và KHÔNG gửi bất cứ thứ gì ra dịch vụ bên ngoài (đã xác nhận với người dùng
 * 2026-09-22 khi chọn giữa 3 hướng webhook) — dữ liệu học sinh không rời hệ thống.
 *
 * Trạng thái nằm trong bộ nhớ của TỪNG instance backend: hiện backend chạy 1 instance mỗi stack nên
 * đủ dùng. Nếu sau này scale nhiều instance, quản trị viên nối vào instance nào chỉ thấy lượt chấm do
 * instance đó xử lý — khi đó phải đổi sang kênh dùng chung (Redis pub/sub) chứ KHÔNG phải sửa chỗ này.
 *
 * Sự kiện đẩy đi cố tình GỌN (bước chấm, số token, tên học sinh, mốc thời gian) — số liệu đầy đủ đã nằm
 * trong DB và lấy qua API thường; kênh này chỉ để trang biết "vừa có lượt mới" và cập nhật con số chạy.
 */
@Service
public class AiTokenUsageStream {

    private static final Logger log = LoggerFactory.getLogger(AiTokenUsageStream.class);

    /** 30 phút — đủ dài cho 1 phiên xem trang, đủ ngắn để kết nối chết tự rụng. */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Trang quản trị mở kết nối; emitter tự gỡ khỏi danh sách khi hết hạn/lỗi/đóng. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

    /**
     * Đẩy 1 lượt chấm vừa xong tới mọi trang đang mở. Gọi từ {@link AiGradingTokenUsageRecorder} SAU khi
     * đã lưu DB — nếu đẩy lỗi thì dòng dữ liệu vẫn còn nguyên trong DB, trang chỉ cần tải lại là thấy.
     */
    public void publish(AiGradingTokenUsage.Step step, AiTokenUsage usage, Student student) {
        if (emitters.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step.name());
        payload.put("studentName", student == null || student.getUser() == null ? null : student.getUser().getFullName());
        payload.put("servedModel", usage.servedModel());
        payload.put("promptTokens", usage.promptTokens());
        payload.put("cachedTokens", usage.cachedTokens());
        payload.put("completionTokens", usage.completionTokens());
        payload.put("reasoningTokens", usage.reasoningTokens());
        payload.put("elapsedMs", usage.elapsedMs());
        payload.put("at", OffsetDateTime.now().toString());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("token-usage").data(payload));
            } catch (IOException | IllegalStateException e) {
                // Trình duyệt đã đóng tab/mất mạng — gỡ khỏi danh sách, KHÔNG ném lên để không ảnh hưởng
                // luồng chấm bài đang chạy ở thread gọi.
                emitters.remove(emitter);
                log.debug("AiTokenUsageStream: gỡ 1 kết nối SSE đã đóng ({}).", e.getMessage());
            }
        }
    }

    /** Số kết nối đang mở — cho endpoint kiểm tra sức khoẻ/chẩn đoán. */
    public int activeSubscribers() {
        return emitters.size();
    }
}
