package vn.com.pps.education.exception;

/** UC-08 — mỗi nhân sự chỉ 1 hợp đồng ACTIVE tại 1 thời điểm (ràng buộc SDD). */
public class ActiveContractAlreadyExistsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ActiveContractAlreadyExistsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ActiveContractAlreadyExistsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
