package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateBookRequest;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateSubTopicRequest;
import vn.com.pps.education.dto.CreateUnitRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.ReviewVideoCatalogImportResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.ReviewVideoRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UC-73: Import Excel hàng loạt "bộ" video ôn tập (Kho Video Ôn tập) —
 * Main Flow, A1 (Sách/Unit/Sub Topic không tồn tại), A2 (chưa có gì để
 * forward-fill), A3 (file hỏng). Xem docs/uc/phan-he-07-lms-portal.md.
 * YouTubeDurationService bị mock để không gọi mạng thật tới Google trong
 * CI (mirror AuthServiceGoogleLoginTest mock GoogleIdTokenVerifier).
 */
@Transactional
class ReviewVideoCatalogImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ReviewVideoCatalogImportService reviewVideoCatalogImportService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private ClassService classService;

    @Autowired
    private ReviewVideoSetRepository reviewVideoSetRepository;

    @Autowired
    private ReviewVideoRepository reviewVideoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @MockBean
    private YouTubeDurationService youTubeDurationService;

    private User teacher;
    private CurriculumResponse curriculum;

    @BeforeEach
    void setUp() {
        User headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");

        CurriculumResponse raw = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        curriculum = curriculumService.update(raw.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        // Bộ/Video nằm dưới curriculum -> requireAssignedTeacherForCurriculum đòi hỏi teacher dạy 1 lớp
        // thuộc khung này (mirror ReviewVideoServiceTest#setUp).
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", newSite().getId(), curriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        curriculumService.addSubTopic(
                curriculumService.addUnit(
                        curriculumService.addBook(curriculum.id(), new CreateBookRequest("Sách A", null)).id(),
                        new CreateUnitRequest("Unit 1", null)).id(),
                new CreateSubTopicRequest("Sub Topic 1", null));

        when(youTubeDurationService.extractVideoId(anyString())).thenAnswer(inv -> "vid-" + inv.getArgument(0).hashCode());
        when(youTubeDurationService.getDurationSeconds(anyString())).thenReturn(300);
    }

    @Test
    void importCatalog_UC73_MainFlow_createsSetAndVideosForwardFillingMergedCells() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"RV-U1-CONN-01", "TKN", curriculum.code(), "GVVN", "Sách A", "Unit 1", "Sub Topic 1", "Video 1", "https://youtu.be/AAAAAAAAAAA"},
                {"", "", "", "", "", "", "", "Video 2", "https://youtu.be/BBBBBBBBBBB"},
        });

        ReviewVideoCatalogImportResponse result = reviewVideoCatalogImportService.importCatalog(
                new MockMultipartFile("file", "bo_video.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);

        ReviewVideoSet set = reviewVideoSetRepository.findByCode("RV-U1-CONN-01").orElseThrow();
        assertThat(set.getVideoType()).isEqualTo(ReviewVideoSet.VideoType.CONNECTION);
        assertThat(set.getTeacherType()).isEqualTo(ReviewVideoSet.TeacherType.VIETNAMESE);
        assertThat(set.getCurriculum().getId()).isEqualTo(curriculum.id());
        assertThat(set.getSubTopic().getTitle()).isEqualTo("Sub Topic 1");
        assertThat(reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId())).hasSize(2);
    }

    @Test
    void importCatalog_UC73_Postcondition_reimportingSameFileSkipsDuplicateVideosInSameSet() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"RV-U1-CONN-01", "PXA", curriculum.code(), "GVNN", "Sách A", "Unit 1", "Sub Topic 1", "Video 1", "https://youtu.be/AAAAAAAAAAA"},
        });

        reviewVideoCatalogImportService.importCatalog(
                new MockMultipartFile("file", "lan1.xlsx", "application/vnd.openxmlformats", file), teacher.getId());
        ReviewVideoSet setBefore = reviewVideoSetRepository.findByCode("RV-U1-CONN-01").orElseThrow();

        ReviewVideoCatalogImportResponse second = reviewVideoCatalogImportService.importCatalog(
                new MockMultipartFile("file", "lan2.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.successRows()).isEqualTo(1);
        ReviewVideoSet setAfter = reviewVideoSetRepository.findByCode("RV-U1-CONN-01").orElseThrow();
        assertThat(setAfter.getId()).isEqualTo(setBefore.getId());
        assertThat(setAfter.getVideoType()).isEqualTo(ReviewVideoSet.VideoType.REFLEX);
        assertThat(reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(setAfter.getId())).hasSize(1);
    }

    @Test
    void importCatalog_UC73_A1_reportsRowErrorWhenUnitNotFoundInCatalog() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"RV-U9-CONN-01", "TKN", curriculum.code(), "GVVN", "Sách A", "Unit không tồn tại", "Sub Topic 1", "Video 1", "https://youtu.be/AAAAAAAAAAA"},
        });

        ReviewVideoCatalogImportResponse result = reviewVideoCatalogImportService.importCatalog(
                new MockMultipartFile("file", "a1.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo(
                "Không tìm thấy Unit 'Unit không tồn tại' trong Sách 'Sách A'.");
    }

    @Test
    void importCatalog_UC73_A2_reportsRowErrorWhenFirstRowHasNothingToForwardFill() throws IOException {
        byte[] file = buildWorkbook(new String[][]{
                {"", "", "", "", "", "", "", "Video 1", "https://youtu.be/AAAAAAAAAAA"},
        });

        ReviewVideoCatalogImportResponse result = reviewVideoCatalogImportService.importCatalog(
                new MockMultipartFile("file", "a2.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason")).isEqualTo("Thiếu Mã bộ và chưa có dòng trước đó để dùng lại.");
    }

    @Test
    void importCatalog_UC73_A3_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        ReviewVideoCatalogImportResponse result = reviewVideoCatalogImportService.importCatalog(
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    private byte[] buildWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("BoVideo");
            Row header = sheet.createRow(0);
            String[] headers = {"Mã bộ", "Loại video", "Mã khung chương trình", "Loại giáo viên", "Tên sách", "Mã Unit", "Mã subtopic", "Tiêu đề", "Link video"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
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
