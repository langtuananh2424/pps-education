package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-67/UC-68: mẫu báo cáo dạng .html — tạo mẫu, phát hiện placeholder
 * (dùng lại đúng logic bracket-based như .docx), cấu hình mapping, xuất
 * PDF qua HTTP thật (mẫu HTML luôn xuất PDF, đã xác nhận với người dùng
 * 2026-08-09). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class HtmlReportTemplateControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String HTML_CONTENT_TYPE = "text/html";

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
    void create_UC67_MainFlow_detectsBracketPlaceholdersSameAsDocx() throws Exception {
        User headAcademic = userWithRole("head.academic.html", "HEAD_ACADEMIC");
        MockMultipartFile file = htmlFile("<p>[STUDENT_NAME] - [STUDENT_CODE]</p>");

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Ho so hoc sinh HTML")
                        .param("templateType", "STUDENT_PROFILE")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("HTML"))
                .andExpect(jsonPath("$.placeholderKeys", org.hamcrest.Matchers.containsInAnyOrder("[STUDENT_NAME]", "[STUDENT_CODE]")));
    }

    @Test
    void generate_UC68_MainFlow_rendersHtmlTemplateToPdfWithStudentData() throws Exception {
        User headAcademic = userWithRole("head.academic.htmlgen", "HEAD_ACADEMIC");
        Student student = newStudent("student.htmlgen");

        MockMultipartFile file = htmlFile("<p>Ho ten: [STUDENT_NAME]</p>");
        String createResponse = mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Ho so hoc sinh HTML")
                        .param("templateType", "STUDENT_PROFILE")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long templateId = objectMapper.readTree(createResponse).get("id").asLong();

        UpdateFieldMappingsRequest mappingRequest = new UpdateFieldMappingsRequest(
                List.of(new FieldMappingItemRequest("[STUDENT_NAME]", "STUDENT_NAME", null)));
        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mappingRequest)))
                .andExpect(status().isOk());

        GenerateReportRequest request = new GenerateReportRequest(templateId, "SINGLE", student.getId(), null, List.of(), null);

        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PDF"))
                .andExpect(jsonPath("$.fileUrl", org.hamcrest.Matchers.endsWith(".pdf")));

        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(r2Client, atLeastOnce()).putObject(
                argThat((PutObjectRequest req) -> req.key().contains("/generated/")), bodyCaptor.capture());
        byte[] pdfBytes = bodyCaptor.getAllValues().get(bodyCaptor.getAllValues().size() - 1)
                .contentStreamProvider().newStream().readAllBytes();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Nguyễn Mạnh Trí");
        }
    }

    private MockMultipartFile htmlFile(String bodyContent) {
        String full = "<html><head><meta charset=\"UTF-8\"></head><body>" + bodyContent + "</body></html>";
        uploadedTemplateBytes = full.getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile("file", "mau.html", HTML_CONTENT_TYPE, uploadedTemplateBytes);
    }

    private Student newStudent(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Nguyễn Mạnh Trí");
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-HTML-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }
}
