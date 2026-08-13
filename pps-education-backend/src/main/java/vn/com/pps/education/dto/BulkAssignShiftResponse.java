package vn.com.pps.education.dto;

import java.util.List;

/** Kết quả gán ca hàng loạt — lỗi của 1 người không rollback người khác, cùng cách EmployeeBatchImportResponse.errorSummary báo lỗi theo dòng. */
public record BulkAssignShiftResponse(
        int successCount,
        List<Failure> failures
) {
    public record Failure(Long employeeId, String reason) {}
}
