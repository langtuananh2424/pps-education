package vn.com.pps.education.exception;

/** UC-36b — contract_number (số hợp đồng liên kết trường) đã tồn tại. */
public class DuplicatePartnerContractNumberException extends RuntimeException {
    public DuplicatePartnerContractNumberException(String message) {
        super(message);
    }
}
