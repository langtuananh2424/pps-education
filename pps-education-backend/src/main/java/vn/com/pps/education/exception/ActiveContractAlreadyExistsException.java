package vn.com.pps.education.exception;

/** UC-08 — mỗi nhân sự chỉ 1 hợp đồng ACTIVE tại 1 thời điểm (ràng buộc SDD). */
public class ActiveContractAlreadyExistsException extends RuntimeException {
    public ActiveContractAlreadyExistsException(String message) {
        super(message);
    }
}
