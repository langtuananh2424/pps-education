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
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.FieldMappingItemRequest;
import vn.com.pps.education.dto.GenerateReportRequest;
import vn.com.pps.education.dto.UpdateFieldMappingsRequest;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractControllerTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-68: Xuất báo cáo từ mẫu — xác nhận qua HTTP thật (MockMvc + JWT ký
 * thật, Testcontainers Postgres). S3Client (Cloudflare R2) bị mock: upload
 * (putObject) trả về thành công, download (getObject) trả lại đúng byte[]
 * file mẫu .docx đã "upload" trong test (mô phỏng round-trip qua R2).
 */
@Transactional
class ReportGenerationControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @MockBean
    private S3Client r2Client;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    private byte[] uploadedTemplateBytes;

    @DynamicPropertySource
    static void mediaR2Config(DynamicPropertyRegistry registry) {
        registry.add("app.media.r2.bucket", () -> "test-bucket");
        registry.add("app.media.r2.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @BeforeEach
    void stubR2() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(r2Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation ->
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(uploadedTemplateBytes))));
    }

    @Test
    void generate_UC68_MainFlow_singleStudentProfileReportSubstitutesRealData() throws Exception {
        User headAcademic = userWithRole("head.academic.gen", "HEAD_ACADEMIC");
        Student student = newStudent("student.gen");
        Long templateId = createStudentProfileTemplate(headAcademic, "Học sinh: [STUDENT_NAME] - Mã: [STUDENT_CODE]");
        configureFieldMappings(headAcademic, templateId,
                new FieldMappingItemRequest("[STUDENT_NAME]", "STUDENT_NAME", null),
                new FieldMappingItemRequest("[STUDENT_CODE]", "STUDENT_CODE", null));

        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), null, java.util.List.of(), null);

        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SINGLE"))
                .andExpect(jsonPath("$.fileFormat").value("DOCX"))
                .andExpect(jsonPath("$.fileUrl", org.hamcrest.Matchers.startsWith(PUBLIC_BASE_URL + "/academic/report-templates/generated/")))
                .andExpect(jsonPath("$.fileUrl", org.hamcrest.Matchers.endsWith(".docx")));
    }

    @Test
    void generate_UC68_A1_missingConfiguredFieldValue_returns400() throws Exception {
        User headAcademic = userWithRole("head.academic.missing", "HEAD_ACADEMIC");
        Student student = newStudent("student.nomapping");
        Long templateId = createStudentProfileTemplate(headAcademic, "PH tài chính: [FINANCIAL_GUARDIAN]");
        // Student chưa có phụ huynh nào -> FINANCIAL_GUARDIAN_NAME không có trong context.
        configureFieldMappings(headAcademic, templateId,
                new FieldMappingItemRequest("[FINANCIAL_GUARDIAN]", "FINANCIAL_GUARDIAN_NAME", null));

        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), null, java.util.List.of(), null);

        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_deniedForRoleWithoutReportGenerate_returns403() throws Exception {
        User headAcademic = userWithRole("head.academic.setupperm", "HEAD_ACADEMIC");
        Student student = newStudent("student.noperm");
        Long templateId = createStudentProfileTemplate(headAcademic, "[STUDENT_NAME]");
        configureFieldMappings(headAcademic, templateId, new FieldMappingItemRequest("[STUDENT_NAME]", "STUDENT_NAME", null));

        User parentUser = userWithRole("parent.noperm", "PARENT");
        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), null, java.util.List.of(), null);

        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(parentUser, "PARENT"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void generate_UC68_boSung_invalidScope_returns400() throws Exception {
        User headAcademic = userWithRole("head.academic.badscope", "HEAD_ACADEMIC");
        Student student = newStudent("student.badscope");
        Long templateId = createStudentProfileTemplate(headAcademic, "[STUDENT_NAME]");
        configureFieldMappings(headAcademic, templateId, new FieldMappingItemRequest("[STUDENT_NAME]", "STUDENT_NAME", null));

        GenerateReportRequest request = new GenerateReportRequest(templateId, "KHONG_HOP_LE", student.getId(), null, java.util.List.of(), null);

        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private Long createStudentProfileTemplate(User actor, String bodyText) throws Exception {
        uploadedTemplateBytes = buildDocx(bodyText);
        MockMultipartFile file = new MockMultipartFile("file", "mau.docx", DOCX_CONTENT_TYPE, uploadedTemplateBytes);
        String response = mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Ho so hoc sinh")
                        .param("templateType", "STUDENT_PROFILE")
                        .header("Authorization", bearerToken(actor, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void configureFieldMappings(User actor, Long templateId, FieldMappingItemRequest... items) throws Exception {
        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(java.util.List.of(items));
        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(actor, "HEAD_ACADEMIC"))
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

    private Student newStudent(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Nguyễn Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-GEN-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }
}
