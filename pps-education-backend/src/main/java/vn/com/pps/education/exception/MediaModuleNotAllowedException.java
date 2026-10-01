package vn.com.pps.education.exception;

/**
 * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng) - tài khoản không được upload vào module
 * media này qua POST /api/media/upload (xem MediaModule.UploadAccess).
 */
public class MediaModuleNotAllowedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public MediaModuleNotAllowedException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
