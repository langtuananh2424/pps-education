package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V192 (2026-09-22, bước ĐO mở đầu cho hướng tối ưu chi phí AI-grading) — kiểm việc đọc token cache/
 * thinking từ field {@code usage} mà 9Router chuyển tiếp từ provider thật.
 *
 * Vì sao phải có test cho thứ nhìn qua chỉ là "đọc vài field JSON": mỗi provider đặt tên field khác
 * nhau, và nếu dò sai tên thì hàm vẫn chạy bình thường, log vẫn in đẹp — chỉ là in ra 0. Số 0 đó lại
 * đúng bằng giá trị nghĩa là "không có cache hit"/"không tốn token thinking", nên lỗi sẽ bị đọc thành
 * KẾT LUẬN NGHIỆP VỤ ngược ("bật cache vô ích", "thinking không đáng kể") thay vì lộ ra như 1 bug. Đây
 * là loại sai lầm không thể phát hiện bằng chạy tay trên staging.
 *
 * Test thuần hàm đọc JSON, không gọi mạng, không cần Spring context (xem .claude/rules/testing.md).
 */
class NineRouterAiClientUsageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NineRouterAiClient client = new NineRouterAiClient(objectMapper, 5);

    private JsonNode usage(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ===================== Token cache =====================

    @Test
    void docTokenCacheTheoShapeOpenAiCompatible() {
        // Shape thực tế của luồng Speaking v2: Gemini qua lớp OpenAI-compatible của 9Router.
        assertThat(client.extractCachedTokens(usage("""
                {"prompt_tokens": 5200, "prompt_tokens_details": {"cached_tokens": 4864}}"""))).isEqualTo(4864);
    }

    @Test
    void docTokenCacheTheoShapeAnthropic() {
        assertThat(client.extractCachedTokens(usage("""
                {"input_tokens": 5200, "cache_read_input_tokens": 4864}"""))).isEqualTo(4864);
    }

    @Test
    void docTokenCacheTheoShapeGeminiGoiThang() {
        assertThat(client.extractCachedTokens(usage("""
                {"promptTokenCount": 5200, "cachedContentTokenCount": 4864}"""))).isEqualTo(4864);
    }

    @Test
    void khongCoTruongCacheNaoThiTra0() {
        // "Không có bằng chứng cache hit" — đúng điều cần biết, KHÔNG được ném lỗi làm hỏng luồng chấm.
        assertThat(client.extractCachedTokens(usage("""
                {"prompt_tokens": 5200, "completion_tokens": 900}"""))).isZero();
    }

    @Test
    void cacheHitBang0VanDocDung0ChuKhongPhaiBoQua() {
        // Phân biệt "provider trả về đúng 0" với "không tìm thấy field" — cả hai cùng ra 0 nên test này
        // chỉ chốt rằng field có mặt và bằng 0 không làm hàm rơi sang nhánh dò tiếp rồi trả rác.
        assertThat(client.extractCachedTokens(usage("""
                {"prompt_tokens": 5200, "prompt_tokens_details": {"cached_tokens": 0}}"""))).isZero();
    }

    // ===================== Token thinking =====================

    @Test
    void docTokenThinkingTheoShapeOpenAiCompatible() {
        assertThat(client.extractReasoningTokens(usage("""
                {"completion_tokens": 1800, "completion_tokens_details": {"reasoning_tokens": 1100}}"""))).isEqualTo(1100);
    }

    @Test
    void docTokenThinkingTheoShapeGeminiSnakeCase() {
        assertThat(client.extractReasoningTokens(usage("""
                {"completion_tokens": 1800, "thoughts_token_count": 1100}"""))).isEqualTo(1100);
    }

    @Test
    void docTokenThinkingTheoShapeGeminiCamelCase() {
        assertThat(client.extractReasoningTokens(usage("""
                {"candidatesTokenCount": 1800, "thoughtsTokenCount": 1100}"""))).isEqualTo(1100);
    }

    @Test
    void khongCoTruongThinkingNaoThiTra0() {
        assertThat(client.extractReasoningTokens(usage("""
                {"prompt_tokens": 5200, "completion_tokens": 900}"""))).isZero();
    }

    // ===================== Đầu vào dị dạng =====================

    @Test
    void usageRongKhongLamHongGiCa() {
        assertThat(client.extractCachedTokens(usage("{}"))).isZero();
        assertThat(client.extractReasoningTokens(usage("{}"))).isZero();
    }

    @Test
    void truongSaiKieuDuLieuThiTra0ChuKhongNemLoi() {
        // 9Router/provider đổi shape là chuyện có thật (xem lịch sử V145/V146) — đo đạc KHÔNG bao giờ
        // được phép làm hỏng luồng chấm của học sinh.
        assertThat(client.extractCachedTokens(usage("""
                {"prompt_tokens_details": {"cached_tokens": "nhieu"}}"""))).isZero();
        assertThat(client.extractReasoningTokens(usage("""
                {"completion_tokens_details": null}"""))).isZero();
    }
}
