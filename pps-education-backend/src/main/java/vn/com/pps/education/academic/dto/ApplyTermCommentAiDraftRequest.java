package vn.com.pps.education.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * UC-76 bước 8 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05) — giáo viên áp dụng các dòng đã chọn
 * từ bản xem trước vào sổ điểm, 1 request cho cả lô (1 giao dịch).
 */
public record ApplyTermCommentAiDraftRequest(@NotEmpty @Size(max = 200) List<@Valid Row> rows) {

    /**
     * @param comment        Nhận xét cuối cùng (đã qua giáo viên sửa nếu có) — ghi vào {@code comment}.
     * @param aiDraftContent nguyên văn bản AI soạn — ghi vào {@code ai_draft_content} để đo mức giáo viên sửa.
     */
    public record Row(@NotNull Long studentId, @NotBlank @Size(max = 4000) String comment,
                      @Size(max = 4000) String aiDraftContent) {
    }
}
