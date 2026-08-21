package vn.com.pps.education.exception;

/** UC-36b — mỗi điểm trường chỉ 1 hợp đồng ACTIVE tại 1 thời điểm (idx_partner_contracts_active). */
public class ActivePartnerContractAlreadyExistsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ActivePartnerContractAlreadyExistsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ActivePartnerContractAlreadyExistsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
