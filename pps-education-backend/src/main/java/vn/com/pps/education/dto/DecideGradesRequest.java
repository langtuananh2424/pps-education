package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * UC-20 Main Flow bước 2-3, A1 (duyệt tách lẻ 1 học sinh trong lô đã
 * submit theo batch — truyền đúng 1 id trong danh sách này).
 */
public record DecideGradesRequest(
        @NotEmpty List<Long> gradeEntryIds,
        @NotBlank String decision,
        String comment
) {}
