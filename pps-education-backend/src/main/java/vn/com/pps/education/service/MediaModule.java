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
 * nhận thêm PDF/Word/Excel/video. LMS_QUESTION (Question.imageUrl, UC-40)
 * cũng bật `acceptsDocuments=true` (2026-07-22) để câu tự luận nhận file
 * PDF/ảnh; video đi kèm theo logic chung không dùng ở FE nhưng vô hại.
 *
 * STUDENT/PARENT/EMPLOYEE: bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng (2026-07-23) - ảnh đại diện (students/parents/employees.
 * portrait_url, V48), đúng như dự kiến ghi chú ở trên ("ảnh đại diện HRM
 * sau này"). `acceptsDocuments=false` vì chỉ nhận ảnh, không cần PDF/video.
 */
public enum MediaModule {
    LMS_QUESTION("lms/questions", true),
    CURRICULUM_DOCUMENT("lms/curriculum-documents", true),
    LESSON_MATERIAL("lms/lesson-materials", true),
    STUDENT("profiles/students", false),
    PARENT("profiles/parents", false),
    EMPLOYEE("profiles/employees", false);

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
