package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionImportResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-23 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — import Excel
 * câu hỏi Kho Video Ôn tập, mirror QuestionImportServiceTest (UC-40): Main
 * Flow tạo câu hỏi hàng loạt, A2 (lỗi 1 dòng không chặn dòng khác), A3 (file
 * đọc hỏng hoàn toàn). Xem docs/uc/phan-he-07-lms-portal.md (UC-23/UC-23b).
 */
@Transactional
class ReviewVideoQuestionImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ReviewVideoQuestionImportService reviewVideoQuestionImportService;

    @Autowired
    private ReviewVideoService reviewVideoService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    private User teacher;
    private CurriculumResponse activeCurriculum;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());
    }

    // ===================== REFLEX =====================

    @Test
    void importReflexQuestions_boSung_MainFlow_createsAllValidRows() throws IOException {
        ReviewVideoResponse video = createReflexVideo();
        byte[] file = buildExcel(
                new String[]{"Mốc thời gian (giây)", "Nội dung câu hỏi", "Thời lượng ghi âm tối đa (giây)", "Số lượt nộp tối đa"},
                new String[][]{
                        {"30", "Describe your favorite hobby.", "60", "3"},
                        {"120", "What did you do last weekend?", "45", null}
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importReflexQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);

        List<ReviewVideoQuestionResponse> saved = reviewVideoService.listQuestions(video.id(), teacher.getId());
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(ReviewVideoQuestionResponse::timestampSeconds).containsExactlyInAnyOrder(30, 120);
        assertThat(saved).filteredOn(q -> q.timestampSeconds() == 30)
                .extracting(ReviewVideoQuestionResponse::maxAttempts).containsExactly(3);
    }

    @Test
    void importReflexQuestions_boSung_A2_oneInvalidRowDoesNotBlockOthers() throws IOException {
        ReviewVideoResponse video = createReflexVideo();
        byte[] file = buildExcel(
                new String[]{"Mốc thời gian (giây)", "Thời lượng ghi âm tối đa (giây)"},
                new String[][]{
                        {"10", "30"},
                        {null, "20"} // thiếu mốc thời gian bắt buộc
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importReflexQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary()).hasSize(1);
        assertThat(reviewVideoService.listQuestions(video.id(), teacher.getId())).hasSize(1);
    }

    @Test
    void importReflexQuestions_boSung_A3_corruptFileMarksJobFailed() {
        ReviewVideoResponse video = createReflexVideo();
        byte[] garbage = "khong phai file excel".getBytes();

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importReflexQuestions(
                video.id(), new MockMultipartFile("file", "hong.xlsx", "application/vnd.openxmlformats", garbage), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(reviewVideoService.listQuestions(video.id(), teacher.getId())).isEmpty();
    }

    @Test
    void importReflexQuestions_boSung_rejectsOverlappingTimestampAcrossRows() throws IOException {
        ReviewVideoResponse video = createReflexVideo();
        byte[] file = buildExcel(
                new String[]{"Mốc thời gian (giây)", "Thời lượng ghi âm tối đa (giây)"},
                new String[][]{
                        {"10", "30"}, // khoảng [10,40)
                        {"20", "10"}  // khoảng [20,30) chồng lấn dòng trên
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importReflexQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("chồng lấn");
        assertThat(reviewVideoService.listQuestions(video.id(), teacher.getId())).hasSize(1);
    }

    // ===================== CONNECTION =====================

    @Test
    void importConnectionQuestions_boSung_MainFlow_createsAllValidRows() throws IOException {
        ReviewVideoResponse video = createConnectionVideo();
        byte[] file = buildExcel(
                new String[]{"Nội dung câu hỏi", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án đúng"},
                new String[][]{
                        {"What is the capital of France?", "London", "Paris", "Berlin", "B"}
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importConnectionQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);

        List<ReviewVideoConnectionQuestionResponse> saved = reviewVideoService.listConnectionQuestions(video.id(), teacher.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).choices()).hasSize(3);
        assertThat(saved.get(0).choices()).filteredOn(c -> c.isCorrect() != null && c.isCorrect())
                .extracting("content").containsExactly("Paris");
    }

    @Test
    void importConnectionQuestions_boSung_A2_rejectsRowWithInvalidCorrectAnswerLetter() throws IOException {
        ReviewVideoResponse video = createConnectionVideo();
        byte[] file = buildExcel(
                new String[]{"Nội dung câu hỏi", "Đáp án A", "Đáp án B", "Đáp án đúng"},
                new String[][]{
                        {"2 + 2 = ?", "3", "4", "Z"} // "Z" không khớp đáp án nào
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importConnectionQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(0);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Đáp án đúng");
    }

    @Test
    void importConnectionQuestions_boSung_A2_rejectsRowMissingBothChoices() throws IOException {
        ReviewVideoResponse video = createConnectionVideo();
        byte[] file = buildExcel(
                new String[]{"Nội dung câu hỏi", "Đáp án A", "Đáp án đúng"},
                new String[][]{
                        {"Thiếu đáp án B", "Chỉ có A", "A"}
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importConnectionQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(0);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("ít nhất 2 đáp án");
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — chỉ hỗ trợ tối đa 5 đáp án (A-E), khớp giới hạn có sẵn của form nhập tay (ConnectionQuizBuilder). */
    @Test
    void importConnectionQuestions_boSung_rejectsFileWithExtraChoiceColumnBeyondE() throws IOException {
        ReviewVideoResponse video = createConnectionVideo();
        byte[] file = buildExcel(
                new String[]{"Nội dung câu hỏi", "Đáp án A", "Đáp án B", "Đáp án F", "Đáp án đúng"},
                new String[][]{
                        {"Câu hỏi 6 đáp án", "1", "2", "3", "A"}
                });

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importConnectionQuestions(
                video.id(), new MockMultipartFile("file", "cau-hoi.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("tối đa 5 đáp án");
        assertThat(reviewVideoService.listConnectionQuestions(video.id(), teacher.getId())).isEmpty();
    }

    @Test
    void importConnectionQuestions_boSung_A3_corruptFileMarksJobFailed() {
        ReviewVideoResponse video = createConnectionVideo();
        byte[] garbage = "khong phai file excel".getBytes();

        ReviewVideoQuestionImportResponse result = reviewVideoQuestionImportService.importConnectionQuestions(
                video.id(), new MockMultipartFile("file", "hong.xlsx", "application/vnd.openxmlformats", garbage), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(reviewVideoService.listConnectionQuestions(video.id(), teacher.getId())).isEmpty();
    }

    // ===================== Helpers =====================

    private ReviewVideoResponse createReflexVideo() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video phản xạ", "REFLEX", activeCurriculum.id(), "VIETNAMESE", null, 1),
                teacher.getId());
        return reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_AUDIO", "Audio", "https://media.pps.edu.vn/lms/review-videos/audio/x.mp3", 1_000_000L, 200, 1, null, null),
                teacher.getId());
    }

    private ReviewVideoResponse createConnectionVideo() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video TKN", "CONNECTION", activeCurriculum.id(), "VIETNAMESE", null, 1),
                teacher.getId());
        return reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4", 1_000_000L, 200, 1, null, null),
                teacher.getId());
    }

    private byte[] buildExcel(String[] headers, String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CauHoi");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String curriculumCode() {
        return "CUR-RVQI-" + SEQ.incrementAndGet();
    }

    private String setCode() {
        return "RV-RVQI-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-RVQI-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-RVQI-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
