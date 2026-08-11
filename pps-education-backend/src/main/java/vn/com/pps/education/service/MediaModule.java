package vn.com.pps.education.service;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-22):
 * `POST /api/media/upload` là API dùng chung (xem MediaController), nhưng
 * cần tách "thư mục" theo module gọi lên để phân biệt trên R2 khi có module
 * khác ngoài LMS cũng upload media (VD ảnh đại diện HRM sau này) - tránh
 * trộn lẫn file của các module khác nhau trong cùng bucket. Thêm module mới
 * = thêm 1 hằng số enum, không sửa MediaStorageService.
 *
 * `acceptsVideo`/`acceptsOfficeDocuments`: bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng (2026-07-22, theo yêu cầu FE; tách thành 2 cờ độc
 * lập 2026-07-27 khi REVIEW_VIDEO ra đời — trước đó gộp chung 1 cờ
 * `acceptsDocuments` khiến không thể "cho video, cấm PDF" cho riêng 1
 * module). CURRICULUM_DOCUMENT (curriculum_documents.file_url, UC-60) và
 * LMS_QUESTION (Question.imageUrl, UC-40) giữ nguyên hành vi kết hợp cũ
 * (nhận cả video lẫn PDF/Word/Excel). REVIEW_VIDEO (review_videos.file_url,
 * UC-23a — đổi tên từ LESSON_MATERIAL) chỉ nhận video, KHÔNG nhận tài liệu
 * văn phòng (Kho Video Ôn tập đã bỏ hẳn PDF/Slide/Word — đã xác nhận với
 * người dùng 2026-07-27).
 *
 * STUDENT/PARENT/EMPLOYEE: bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng (2026-07-23) - ảnh đại diện (students/parents/employees.
 * portrait_url, V48). Cả 2 cờ `false` vì chỉ nhận ảnh, không cần PDF/video.
 *
 * REVIEW_VIDEO_SUBMISSION: bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng (2026-07-27, UC-23b) - audio Học sinh nộp trả lời cho video REFLEX
 * (review_video_submissions.audio_url). Tách folder riêng với REVIEW_VIDEO
 * (file Giáo viên upload) dù cùng module Kho Video Ôn tập, để không trộn
 * lẫn nội dung GV tạo với nội dung HS nộp trên R2. Cả 2 cờ `false` vì chỉ
 * nhận audio (mọi module đã được nhận audio/* mặc định, xem MediaStorageService).
 *
 * EXERCISE_ANSWER_SUBMISSION: bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng (2026-08-04, V85) - audio Học sinh nộp cho câu hỏi SPEAKING
 * (Speaking oral gốc lẫn "Nghe & nộp audio" mới, student_answers.
 * audio_answer_url) — tách folder khỏi LMS_QUESTION (audio mẫu GV tạo,
 * questions.audio_url) cùng lý do với REVIEW_VIDEO_SUBMISSION ở trên.
 *
 * REPORT_TEMPLATE: bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * (2026-08-09, UC-67) - file mẫu báo cáo (DOCX/PDF) đã đánh dấu
 * placeholder do Trưởng phòng đào tạo/Quản lý điểm trường upload
 * (report_templates.file_url). Nhận thêm PDF/Word (acceptsOfficeDocuments
 * =true) vì mẫu chỉ là DOCX/PDF, không nhận video.
 */
public enum MediaModule {
    LMS_QUESTION("lms/questions", true, true),
    CURRICULUM_DOCUMENT("lms/curriculum-documents", true, true),
    REVIEW_VIDEO("lms/review-videos", true, false),
    REVIEW_VIDEO_SUBMISSION("lms/review-video-submissions", false, false),
    EXERCISE_ANSWER_SUBMISSION("lms/exercise-answer-submissions", false, false),
    STUDENT("profiles/students", false, false),
    PARENT("profiles/parents", false, false),
    EMPLOYEE("profiles/employees", false, false),
    REPORT_TEMPLATE("academic/report-templates", false, true);

    private final String folderPrefix;
    private final boolean acceptsVideo;
    private final boolean acceptsOfficeDocuments;

    MediaModule(String folderPrefix, boolean acceptsVideo, boolean acceptsOfficeDocuments) {
        this.folderPrefix = folderPrefix;
        this.acceptsVideo = acceptsVideo;
        this.acceptsOfficeDocuments = acceptsOfficeDocuments;
    }

    public String folderPrefix() {
        return folderPrefix;
    }

    /** true nếu module này được nhận thêm video/* ngoài audio/ảnh. */
    public boolean acceptsVideo() {
        return acceptsVideo;
    }

    /** true nếu module này được nhận thêm PDF/Word/Excel/PowerPoint ngoài audio/ảnh. */
    public boolean acceptsOfficeDocuments() {
        return acceptsOfficeDocuments;
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
