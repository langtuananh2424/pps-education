package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-67/UC-68: mẫu báo cáo dạng .pdf (PDF Form/AcroForm) — tạo mẫu, phát
 * hiện field, cấu hình mapping, xuất báo cáo qua HTTP thật. Xem
 * docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class PdfReportTemplateControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

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
    void create_UC67_MainFlow_detectsAcroFormFieldsAsPlaceholders() throws Exception {
        User headAcademic = userWithRole("head.academic.pdf", "HEAD_ACADEMIC");
        MockMultipartFile file = pdfFormFile("mau.pdf", "STUDENT_NAME", "STUDENT_CODE");

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Ho so hoc sinh PDF")
                        .param("templateType", "STUDENT_PROFILE")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileFormat").value("PDF"))
                .andExpect(jsonPath("$.placeholderKeys", org.hamcrest.Matchers.containsInAnyOrder("STUDENT_NAME", "STUDENT_CODE")));
    }

    @Test
    void create_UC67_A1_rejectsPdfWithoutAcroForm_savesWithEmptyPlaceholders() throws Exception {
        User headAcademic = userWithRole("head.academic.pdfnoform", "HEAD_ACADEMIC");
        byte[] plainPdf = buildPlainPdf();
        MockMultipartFile file = new MockMultipartFile("file", "mau.pdf", PDF_CONTENT_TYPE, plainPdf);
        uploadedTemplateBytes = plainPdf;

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Mau khong co form")
                        .param("templateType", "STUDENT_PROFILE")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeholderKeys", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void generate_UC68_MainFlow_fillsPdfFormWithStudentData() throws Exception {
        User headAcademic = userWithRole("head.academic.pdfgen", "HEAD_ACADEMIC");
        Student student = newStudent("student.pdfgen");

        MockMultipartFile file = pdfFormFile("mau.pdf", "STUDENT_NAME", "STUDENT_CODE");
        String createResponse = mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Ho so hoc sinh PDF")
                        .param("templateType", "STUDENT_PROFILE")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long templateId = objectMapper.readTree(createResponse).get("id").asLong();

        UpdateFieldMappingsRequest mappingRequest = new UpdateFieldMappingsRequest(List.of(
                new FieldMappingItemRequest("STUDENT_NAME", "STUDENT_NAME", null),
                new FieldMappingItemRequest("STUDENT_CODE", "STUDENT_CODE", null)));
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
    }

    private MockMultipartFile pdfFormFile(String filename, String... fieldNames) throws IOException {
        uploadedTemplateBytes = buildPdfForm(fieldNames);
        return new MockMultipartFile("file", filename, PDF_CONTENT_TYPE, uploadedTemplateBytes);
    }

    private byte[] buildPlainPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdfForm(String... fieldNames) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA);
            acroForm.setDefaultResources(resources);
            acroForm.setDefaultAppearance("/Helv 12 Tf 0 g");

            int y = 700;
            for (String fieldName : fieldNames) {
                PDTextField textField = new PDTextField(acroForm);
                textField.setPartialName(fieldName);
                textField.setDefaultAppearance("/Helv 12 Tf 0 g");

                PDAnnotationWidget widget = textField.getWidgets().get(0);
                widget.setRectangle(new PDRectangle(50, y, 200, 20));
                widget.setPage(page);
                page.getAnnotations().add(widget);
                acroForm.getFields().add(textField);
                y -= 30;
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private Student newStudent(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-PDF-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }
}
