package vn.com.pps.education.exception;

/** UC-17 Postcondition — khung chương trình tùy biến (site_id NOT NULL) chỉ dùng được cho đúng điểm trường đó. */
public class CurriculumNotAvailableForSiteException extends RuntimeException {
    public CurriculumNotAvailableForSiteException(String message) {
        super(message);
    }
}
