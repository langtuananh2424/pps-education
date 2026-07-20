package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-20 Công bố điểm: công bố (DRAFT -> PUBLISHED) từng bản ghi hoặc theo lô.
 * Từ UC-53, có thể công bố kèm (hoặc riêng) các bản ghi Overall/Level
 * (gradePeriodResultIds) — 2 danh sách đều optional nhưng ít nhất 1 danh
 * sách phải có phần tử (validate ở service). Không còn field
 * decision/comment — V39 bỏ hẳn nhánh từ chối (bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng).
 */
public record PublishGradesRequest(
        List<Long> gradeEntryIds,
        List<Long> gradePeriodResultIds
) {}
