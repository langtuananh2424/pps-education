package vn.com.pps.education.exception;

/** UC-36b — mỗi điểm trường chỉ 1 hợp đồng ACTIVE tại 1 thời điểm (idx_partner_contracts_active). */
public class ActivePartnerContractAlreadyExistsException extends RuntimeException {
    public ActivePartnerContractAlreadyExistsException(String message) {
        super(message);
    }
}
