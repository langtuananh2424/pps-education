package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * UC-74 bước 9 — giáo viên trò chuyện với trợ lý để sửa bản nháp. Không lưu phiên trò chuyện ở backend:
 * FE gửi kèm bản nháp hiện tại + vài lượt trao đổi gần nhất.
 *
 * @param mode {@code INSTRUCTION}: sửa theo {@code instruction} (chỉ các dòng liên quan);
 *             {@code REWRITE_ALL}: viết lại câu chữ toàn bộ từ {@code extraction}, giữ nguyên ý.
 */
public record ReviseCommentAiDraftRequest(
        @NotNull Mode mode,
        String instruction,
        String transcript,
        CommentAiDraftResult.Extraction extraction,
        @NotNull List<@Valid CurrentRow> currentRows,
        List<ChatTurn> history
) {

    public enum Mode { INSTRUCTION, REWRITE_ALL }

    public record CurrentRow(@NotNull Long studentId, String attitude, String content) {
    }

    /** @param role {@code teacher} hoặc {@code assistant}. */
    public record ChatTurn(String role, String text) {
    }
}
