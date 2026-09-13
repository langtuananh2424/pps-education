package vn.com.pps.education.exception;

/**
 * UC-01 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13) — tài khoản Học sinh đã có
 * refresh token ACTIVE (chưa revoke, chưa hết hạn) trên 1 thiết bị khác. Chặn đăng nhập tiếp ở thiết
 * bị thứ 2 cho tới khi đăng xuất thiết bị 1 — tránh học sinh dùng 2 thiết bị cùng lúc để "lách luật"
 * (VD 1 máy mở sẵn đề để tra cứu, máy còn lại làm bài). Xem Javadoc AuthService#login.
 */
public class ActiveSessionExistsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ActiveSessionExistsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ActiveSessionExistsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
