package vn.com.pps.education.exception;

/**
 * UC-53 A1 — có cột trong file Excel không khớp bất kỳ thành phần điểm/
 * Overall/Level nào đã cấu hình cho kỳ đánh giá: dừng toàn bộ import,
 * message liệt kê danh sách header không khớp.
 */
public class GradeImportColumnMismatchException extends RuntimeException {

    public GradeImportColumnMismatchException(String message) {
        super(message);
    }
}
