package vn.com.pps.education.service;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22):
 * `POST /api/media/upload` là API dùng chung (xem MediaController), nhưng
 * cần tách "thư mục" theo module gọi lên để phân biệt trên R2 khi có module
 * khác ngoài LMS cũng upload media (VD ảnh đại diện HRM sau này) - tránh
 * trộn lẫn file của các module khác nhau trong cùng bucket. Chỉ có
 * LMS_QUESTION là module thật đang gọi API này (UC-40); thêm module mới =
 * thêm 1 hằng số enum, không sửa MediaStorageService.
 */
public enum MediaModule {
    LMS_QUESTION("lms/questions");

    private final String folderPrefix;

    MediaModule(String folderPrefix) {
        this.folderPrefix = folderPrefix;
    }

    public String folderPrefix() {
        return folderPrefix;
    }

    public static MediaModule fromCode(String code) {
        try {
            return MediaModule.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Module không hợp lệ: " + code
                    + ". Giá trị hợp lệ: " + java.util.Arrays.toString(MediaModule.values()));
        }
    }
}
