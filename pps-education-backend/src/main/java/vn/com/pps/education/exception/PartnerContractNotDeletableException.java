package vn.com.pps.education.exception;

/** UC-36b A3 — chỉ xóa (mềm) được hợp đồng đang DRAFT, chưa từng có hiệu lực pháp lý. */
public class PartnerContractNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public PartnerContractNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public PartnerContractNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
