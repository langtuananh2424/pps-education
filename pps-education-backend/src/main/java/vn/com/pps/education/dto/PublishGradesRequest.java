package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-20 Duyệt/Từ chối điểm: V44 (bổ sung ngoài SDD gốc, thay thế luồng
 * "công bố dự kiến + phúc khảo"): duyệt (SUBMITTED -> OFFICIAL) hoặc từ
 * chối (SUBMITTED -> REJECTED) từng bản ghi hoặc theo lô. Từ UC-53, có thể
 * duyệt/từ chối kèm (hoặc riêng) các bản ghi Overall/Level
 * (gradeEvaluationResultIds) — 2 danh sách đều optional nhưng ít nhất 1
 * danh sách phải có phần tử (validate ở service). Field action: APPROVE / REJECT.
 * Field rejectReason: optional, ghi lý do khi REJECT.
 *
 * V95 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): Quản lý điểm
 * trường được sửa "Nhận xét"/"Ghi chú" NGAY TRƯỚC KHI công bố (chỉ áp
 * dụng nhánh APPROVE, chỉ trên GradeEvaluationResult — không sửa được
 * điểm/score). {@code evaluationResultComments}/{@code evaluationResultNotes}
 * (id -> nội dung mới) optional — chỉ set khi Quản lý muốn chỉnh nội
 * dung khác với GV đã nhập trước khi công bố.
 */
public record PublishGradesRequest(
        String action,  // APPROVE / REJECT
        List<Long> gradeEntryIds,
        List<Long> gradeEvaluationResultIds,
        String rejectReason,  // optional, dùng khi REJECT
        Map<Long, String> evaluationResultComments,  // optional, id -> "Nhận xét" mới, chỉ dùng khi APPROVE
        Map<Long, String> evaluationResultNotes      // optional, id -> "Ghi chú" mới, chỉ dùng khi APPROVE
) {}
