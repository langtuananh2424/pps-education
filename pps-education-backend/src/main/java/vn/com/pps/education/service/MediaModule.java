package vn.com.pps.education.service;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22):
 * `POST /api/media/upload` là API dùng chung (xem MediaController), nhưng
 * cần tách "thư mục" theo module gọi lên để phân biệt trên R2 khi có module
 * khác ngoài LMS cũng upload media (VD ảnh đại diện HRM sau này) - tránh
 * trộn lẫn file của các module khác nhau trong cùng bucket. Thêm module mới
 * = thêm 1 hằng số enum, không sửa MediaStorageService.
 *
 * `acceptsDocuments`: bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * (2026-07-22, theo yêu cầu FE) - CURRICULUM_DOCUMENT (curriculum_documents.
 * file_url, UC-60) và LESSON_MATERIAL (lesson_materials.file_url, UC-23a)
 * trước đây là field nhập tay URL, giờ cũng upload thật qua API này nên cần
 * nhận thêm PDF/Word/Excel/video (LMS_QUESTION giữ nguyên, chỉ audio/ảnh).
 */
public enum MediaModule {
    LMS_QUESTION("lms/questions", false),
    CURRICULUM_DOCUMENT("lms/curriculum-documents", true),
    LESSON_MATERIAL("lms/lesson-materials", true);

    private final String folderPrefix;
    private final boolean acceptsDocuments;

    MediaModule(String folderPrefix, boolean acceptsDocuments) {
        this.folderPrefix = folderPrefix;
        this.acceptsDocuments = acceptsDocuments;
    }

    public String folderPrefix() {
        return folderPrefix;
    }

    /** true nếu module này được nhận thêm PDF/Word/Excel/PowerPoint/video ngoài audio/ảnh. */
    public boolean acceptsDocuments() {
        return acceptsDocuments;
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
