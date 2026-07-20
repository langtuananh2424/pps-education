package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * UC-20 Main Flow bước 2-3, A1 (duyệt tách lẻ 1 học sinh trong lô đã
 * submit theo batch — truyền đúng 1 id trong danh sách này). Từ UC-53,
 * có thể duyệt kèm (hoặc riêng) các bản ghi Overall/Level
 * (gradePeriodResultIds) — 2 danh sách đều optional nhưng ít nhất 1
 * danh sách phải có phần tử (validate ở service).
 */
public record DecideGradesRequest(
        List<Long> gradeEntryIds,
        List<Long> gradePeriodResultIds,
        @NotBlank String decision,
        String comment
) {}
