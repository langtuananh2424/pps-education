package vn.com.pps.education.exception;

/** UC-08 — số hợp đồng (contract_number) đã tồn tại. */
public class DuplicateContractNumberException extends RuntimeException {
    public DuplicateContractNumberException(String message) {
        super(message);
    }
}
