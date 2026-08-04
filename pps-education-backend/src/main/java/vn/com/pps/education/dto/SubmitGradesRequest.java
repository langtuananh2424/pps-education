package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-19 Main Flow bước 4 (V44, bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng): Giáo viên gửi duyệt điểm — DRAFT hoặc REJECTED (gửi lại
 * sau khi sửa theo yêu cầu Quản lý) chuyển sang SUBMITTED, chờ Quản lý
 * điểm trường duyệt qua UC-20. 2 danh sách đều optional nhưng ít nhất 1
 * danh sách phải có phần tử (validate ở service).
 */
public record SubmitGradesRequest(
        List<Long> gradeEntryIds,
        List<Long> gradePeriodResultIds
) {}
