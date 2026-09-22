package vn.com.pps.education.service;

import vn.com.pps.education.common.AiTokenUsage;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — nơi {@link NineRouterAiClient}
 * báo lại những lệnh gọi mà nó KHÔNG thể trả usage ngược lên cho service gọi, cụ thể là lệnh gọi bị
 * LOẠI KẾT QUẢ (9Router trả sai model so với {@code expected-model-contains}, hoặc nội dung rỗng):
 * những lượt đó trả {@code null} nên service gọi không nhận được gì, NHƯNG token đã tiêu thụ thật và vẫn
 * bị nhà cung cấp tính tiền. Không ghi lại thì tổng trên trang Quản trị sẽ THẤP HƠN hoá đơn mà không ai
 * biết vì sao — đúng kiểu sai lệch âm thầm.
 *
 * Dòng ghi qua đây KHÔNG có ngữ cảnh học sinh/bài tập (client tầng dưới cố tình không biết gì về học
 * sinh — xem .claude/rules/architecture.md), nên trang quản trị hiển thị chúng ở nhóm "không rõ ngữ
 * cảnh". Đó là đánh đổi có chủ đích: thà biết "có 12.000 token bị bỏ đi" còn hơn im lặng bỏ sót.
 *
 * Là interface (không gọi thẳng service ghi DB) để {@link NineRouterAiClient} không phụ thuộc vào tầng
 * lưu trữ và để test dựng được bản {@link #NO_OP} — nguyên tắc D trong .claude/rules/solid.md.
 */
public interface AiUsageSink {

    /** Bản rỗng cho unit test và cho cấu hình chưa bật ghi DB — không làm gì, không ném lỗi. */
    AiUsageSink NO_OP = (operation, requestedModel, usage) -> { };

    /**
     * @param operation      tên kỹ thuật của lệnh gọi (chat/chatJson/chatWithAudioJson/transcribe).
     * @param requestedModel model đã yêu cầu (usage đã mang sẵn model thực tế được route tới).
     */
    void recordRejected(String operation, String requestedModel, AiTokenUsage usage);
}
