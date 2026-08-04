package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-20 Duyệt/Từ chối điểm: V44 (bổ sung ngoài SDD gốc, thay thế luồng
 * "công bố dự kiến + phúc khảo"): duyệt (SUBMITTED -> OFFICIAL) hoặc từ
 * chối (SUBMITTED -> REJECTED) từng bản ghi hoặc theo lô. Từ UC-53, có thể
 * duyệt/từ chối kèm (hoặc riêng) các bản ghi Overall/Level
 * (gradePeriodResultIds) — 2 danh sách đều optional nhưng ít nhất 1 danh
 * sách phải có phần tử (validate ở service). Field action: APPROVE / REJECT.
 * Field rejectReason: optional, ghi lý do khi REJECT.
 */
public record PublishGradesRequest(
        String action,  // APPROVE / REJECT
        List<Long> gradeEntryIds,
        List<Long> gradePeriodResultIds,
        String rejectReason  // optional, dùng khi REJECT
) {}
