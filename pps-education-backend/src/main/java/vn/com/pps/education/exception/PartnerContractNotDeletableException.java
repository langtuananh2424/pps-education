package vn.com.pps.education.exception;

/** UC-36b A3 — chỉ xóa (mềm) được hợp đồng đang DRAFT, chưa từng có hiệu lực pháp lý. */
public class PartnerContractNotDeletableException extends RuntimeException {
    public PartnerContractNotDeletableException(String message) {
        super(message);
    }
}
