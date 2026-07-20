package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-19 Main Flow bước 4: submit từng bản ghi (1 phần tử) hoặc theo lô
 * (nhiều phần tử, sinh chung 1 batch_id). Từ UC-53, có thể submit kèm
 * (hoặc riêng) các bản ghi Overall/Level (gradePeriodResultIds) — 2 danh
 * sách đều optional nhưng ít nhất 1 danh sách phải có phần tử (validate
 * ở service).
 */
public record SubmitGradesRequest(
        List<Long> gradeEntryIds,
        List<Long> gradePeriodResultIds
) {}
