package vn.com.pps.education.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * UC-76 bước 7b (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-06) — giáo viên trò chuyện với trợ lý để sửa
 * bản nháp Nhận xét kỳ. Không lưu phiên trò chuyện ở backend: FE gửi kèm bản nháp hiện tại + vài lượt trao đổi gần nhất.
 * Yêu cầu sửa (chữ) và/hoặc audio đi riêng ở các part {@code instruction}/{@code audio} của multipart.
 *
 * @param mode {@code INSTRUCTION}: sửa theo yêu cầu, chỉ các dòng liên quan; {@code REWRITE_ALL}: viết lại câu chữ toàn bộ.
 */
public record ReviseTermCommentAiDraftRequest(
        @NotNull Mode mode,
        @NotEmpty @Size(max = 200) List<@Valid CurrentRow> currentRows,
        @Size(max = 12) List<ChatTurn> history
) {

    public enum Mode { INSTRUCTION, REWRITE_ALL }

    public record CurrentRow(@NotNull Long studentId, @Size(max = 4000) String content) {
    }

    /** @param role {@code teacher} hoặc {@code assistant}. */
    public record ChatTurn(String role, @Size(max = 4000) String text) {
    }
}
