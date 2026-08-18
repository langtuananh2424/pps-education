package vn.com.pps.education.exception;

/**
 * Exception nào cần trả message song ngữ thì implement thêm interface này (bên cạnh constructor
 * String message cũ, giữ nguyên không đổi) — xem GlobalExceptionHandler.error(status, ex).
 * messageKey() trả về null nghĩa là exception được tạo qua constructor cũ (chưa migrate), FE vẫn
 * nhận đúng message tiếng Việt cứng như trước, không đổi hành vi.
 */
public interface LocalizedMessage {
    String messageKey();

    Object[] messageArgs();
}
