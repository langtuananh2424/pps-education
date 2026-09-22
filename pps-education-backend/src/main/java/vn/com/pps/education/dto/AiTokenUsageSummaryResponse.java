package vn.com.pps.education.dto;

import java.util.List;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — dữ liệu trang Quản trị hệ thống
 * → Sử dụng token AI, gộp sẵn theo 3 chiều mà người dùng yêu cầu: học sinh, bài tập, bước chấm.
 *
 * Mọi số token đều tách riêng {@code cachedTokens} và {@code reasoningTokens} thay vì gộp vào
 * prompt/completion, vì 2 loại này có ý nghĩa chi phí khác hẳn: token cache rẻ hơn nhiều lần token
 * thường, còn token thinking tính giá như output nhưng không hiện ra trong nội dung trả về.
 */
public record AiTokenUsageSummaryResponse(Totals totals, List<ByStep> byStep, List<ByStudent> byStudent,
                                          List<ByAssignment> byAssignment) {

    /**
     * @param rejectedCalls số lượt bị loại kết quả (9Router trả sai model / nội dung rỗng) — VẪN tính
     *                      tiền. Tách ra để nhìn thấy phần chi phí bỏ đi thay vì giấu trong tổng.
     * @param unattributedCalls số lượt không gắn được học sinh (luồng chấm Writing UC-40/41 và lượt bị
     *                      loại) — giải thích vì sao tổng theo học sinh nhỏ hơn tổng chung.
     */
    public record Totals(long callCount, long promptTokens, long cachedTokens, long completionTokens,
                         long reasoningTokens, long rejectedCalls, long unattributedCalls) {
    }

    public record ByStep(String step, long callCount, long promptTokens, long cachedTokens, long completionTokens,
                         long reasoningTokens, long avgElapsedMs) {
    }

    public record ByStudent(Long studentId, String studentName, long callCount, long promptTokens, long cachedTokens,
                            long completionTokens, long reasoningTokens) {
    }

    public record ByAssignment(Long assignmentId, String assignmentName, long callCount, long promptTokens,
                               long cachedTokens, long completionTokens, long reasoningTokens) {
    }
}
