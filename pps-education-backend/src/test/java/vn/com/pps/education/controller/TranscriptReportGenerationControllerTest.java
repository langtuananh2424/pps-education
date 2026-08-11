package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.FieldMappingItemRequest;
import vn.com.pps.education.dto.GenerateReportRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.ReportPeriodSelector;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateFieldMappingsRequest;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.service.ClassService;
import vn.com.pps.education.service.CurriculumService;
import vn.com.pps.education.service.GradeService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-68: Xuất phiếu kết quả lộ trình (TRANSCRIPT) — xác nhận xuất linh
 * hoạt theo giữa kỳ/cuối kỳ/cả kỳ (đã xác nhận với người dùng 2026-08-09:
 * actor tự chọn 1 hoặc nhiều {@link ReportPeriodSelector}, không có quy
 * ước "S1/S2" cố định trong hệ thống). Dựng fixture điểm số theo đúng
 * pattern GradeServiceTest (GradeComponentSetup gắn lớp+kỳ học+Giữa/Cuối
 * kỳ, xem V95).
 */
@Transactional
class TranscriptReportGenerationControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @MockBean
    private S3Client r2Client;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private AcademicTerm academicTerm;
    private Student student;
    private byte[] uploadedTemplateBytes;

    @DynamicPropertySource
    static void mediaR2Config(DynamicPropertyRegistry registry) {
        registry.add("app.media.r2.bucket", () -> "test-bucket");
        registry.add("app.media.r2.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @BeforeEach
    void setUp() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(r2Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation ->
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(uploadedTemplateBytes))));

        headAcademic = newUser("head.academic.transcript");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        academicTerm = newAcademicTerm(site);
        schoolClass = classService.create(
                new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "7A1", site.getId(), activeCurriculum.id(), "OPEN", 20,
                        null, LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher.transcript");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        student = newStudent();
    }

    @Test
    void generate_UC68_MainFlow_endTermOnly_substitutesReadingScoreAndOverall() throws Exception {
        GradeComponentSetupResponse endTermSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "END_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        GradeEvaluationComponentResponse readingComponent = gradeService.addGradeEvaluationComponent(endTermSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "READING", "Đọc", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());
        gradeService.enterGrade(schoolClass.id(), readingComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.5"), false, null), teacher.getId());
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), endTermSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("8.0"), "NUMERIC", "A2", null, null, null), teacher.getId());

        Long templateId = createTranscriptTemplate("Doc: [READING_END1] - TB: [OVERALL_END1] - Level: [LEVEL_END1]");
        configureFieldMappings(templateId,
                new FieldMappingItemRequest("[READING_END1]", "READING_END1", null),
                new FieldMappingItemRequest("[OVERALL_END1]", "OVERALL_END1", null),
                new FieldMappingItemRequest("[LEVEL_END1]", "LEVEL_END1", null));

        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), schoolClass.id(),
                List.of(new ReportPeriodSelector("END1", academicTerm.getId(), "END_TERM")), null);

        String mergedText = generateAndExtractText(request);

        assertThat(mergedText).isEqualTo("Doc: 8.50 - TB: 8 - Level: A2");
    }

    @Test
    void generate_UC68_MainFlow_fullTermCombinesMidAndEndTermInSameDocument() throws Exception {
        GradeComponentSetupResponse midSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        GradeComponentSetupResponse endSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "END_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), midSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.5"), "NUMERIC", "A1", null, null, null), teacher.getId());
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), endSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("8.0"), "NUMERIC", "A2", null, null, null), teacher.getId());

        Long templateId = createTranscriptTemplate("Giữa kỳ: [OVERALL_MID1] - Cuối kỳ: [OVERALL_END1]");
        configureFieldMappings(templateId,
                new FieldMappingItemRequest("[OVERALL_MID1]", "OVERALL_MID1", null),
                new FieldMappingItemRequest("[OVERALL_END1]", "OVERALL_END1", null));

        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), schoolClass.id(),
                List.of(new ReportPeriodSelector("MID1", academicTerm.getId(), "MID_TERM"),
                        new ReportPeriodSelector("END1", academicTerm.getId(), "END_TERM")), null);

        String mergedText = generateAndExtractText(request);

        assertThat(mergedText).isEqualTo("Giữa kỳ: 6.50 - Cuối kỳ: 8");
    }

    @Test
    void generate_UC68_A1_periodNotEnteredYet_returns400() throws Exception {
        Long templateId = createTranscriptTemplate("[OVERALL_END1]");
        configureFieldMappings(templateId, new FieldMappingItemRequest("[OVERALL_END1]", "OVERALL_END1", null));

        // Không tạo setup/nhập điểm nào cho academicTerm này -> OVERALL_END1 không có trong context.
        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), schoolClass.id(),
                List.of(new ReportPeriodSelector("END1", academicTerm.getId(), "END_TERM")), null);

        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private String generateAndExtractText(GenerateReportRequest request) throws Exception {
        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<RequestBody> bodyCaptor = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        org.mockito.Mockito.verify(r2Client, org.mockito.Mockito.atLeastOnce()).putObject(
                argThat((PutObjectRequest req) -> req.key().contains("/generated/")), bodyCaptor.capture());
        RequestBody lastGeneratedBody = bodyCaptor.getAllValues().get(bodyCaptor.getAllValues().size() - 1);
        byte[] mergedBytes = lastGeneratedBody.contentStreamProvider().newStream().readAllBytes();
        return extractText(mergedBytes);
    }

    private Long createTranscriptTemplate(String bodyText) throws Exception {
        uploadedTemplateBytes = buildDocx(bodyText);
        MockMultipartFile file = new MockMultipartFile("file", "mau.docx", DOCX_CONTENT_TYPE, uploadedTemplateBytes);
        String response = mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Phieu ket qua lo trinh")
                        .param("templateType", "TRANSCRIPT")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void configureFieldMappings(Long templateId, FieldMappingItemRequest... items) throws Exception {
        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(List.of(items));
        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private byte[] buildDocx(String paragraphText) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(paragraphText);
            document.write(out);
            return out.toByteArray();
        }
    }

    private String extractText(byte[] docxBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : document.getParagraphs()) {
                sb.append(p.getText());
            }
            return sb.toString();
        }
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
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private AcademicTerm newAcademicTerm(Site site) {
        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
        term.setCode("TERM-" + SEQ.incrementAndGet());
        term.setName("Học kỳ 1");
        term.setStartDate(LocalDate.now().minusMonths(1));
        term.setEndDate(LocalDate.now().plusMonths(2));
        term.setCreatedBy(headAcademic);
        return academicTermRepository.save(term);
    }

    private Student newStudent() {
        User user = newUser("student.transcript");
        Student s = new Student();
        s.setUser(user);
        s.setStudentCode("HS-TR-" + SEQ.incrementAndGet());
        s.setDateOfBirth(LocalDate.of(2012, 5, 1));
        s.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(s);
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
