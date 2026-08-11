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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.FieldMappingItemRequest;
import vn.com.pps.education.dto.UpdateFieldMappingsRequest;
import vn.com.pps.education.dto.UpdateReportTemplateRequest;
import vn.com.pps.education.support.AbstractControllerTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-67: Quản lý mẫu báo cáo — xác nhận qua HTTP thật (MockMvc + JWT ký
 * thật, Testcontainers Postgres) rằng @PreAuthorize("hasPermission(...)")
 * chặn/cho phép đúng theo quyền report.template.create/view/update/delete,
 * và Main Flow/Alternate Flow hoạt động đúng đặc tả trong
 * docs/uc/phan-he-06-hoc-thuat.md. S3Client (Cloudflare R2) bị mock để
 * không gọi mạng thật trong CI.
 */
@Transactional
class ReportTemplateControllerTest extends AbstractControllerTest {

    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @MockBean
    private S3Client r2Client;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void mediaR2Config(DynamicPropertyRegistry registry) {
        registry.add("app.media.r2.bucket", () -> "test-bucket");
        registry.add("app.media.r2.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @BeforeEach
    void stubR2Upload() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    @Test
    void create_UC67_MainFlow_detectsPlaceholdersAndSavesTemplate() throws Exception {
        User headAcademic = userWithRole("head.academic.report", "HEAD_ACADEMIC");
        MockMultipartFile file = docxFile("mau.docx",
                "Họ tên: [STUDENT_NAME]\nLớp: [CLASS_NAME]\n"
                        + "Điểm TB: [[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]");

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Phiếu kết quả lộ trình")
                        .param("templateType", "TRANSCRIPT")
                        .param("description", "Mau nam hoc")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateType").value("TRANSCRIPT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.placeholderKeys", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.placeholderKeys",
                        org.hamcrest.Matchers.hasItems("[STUDENT_NAME]", "[CLASS_NAME]",
                                "[[[READING]+[LISTENING]+[SPEAKING]+[WRITING]]/4]")));
    }

    @Test
    void create_UC67_A1_rejectsNonDocxFile_returns400() throws Exception {
        User headAcademic = userWithRole("head.academic.badformat", "HEAD_ACADEMIC");
        MockMultipartFile file = new MockMultipartFile("file", "mau.pdf", "application/pdf", "fake-pdf".getBytes());

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Phiếu kết quả lộ trình")
                        .param("templateType", "TRANSCRIPT")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_UC67_A2_savesTemplateWithEmptyPlaceholderListWhenNoneDetected() throws Exception {
        User headAcademic = userWithRole("head.academic.noplaceholder", "HEAD_ACADEMIC");
        MockMultipartFile file = docxFile("mau-tinh.docx", "Không có trường nào cần điền ở đây.");

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Mẫu tĩnh")
                        .param("templateType", "DAILY_REPORT")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeholderKeys", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void create_UC67_A3_rejectsUnbalancedFormulaBrackets_returns400() throws Exception {
        User headAcademic = userWithRole("head.academic.badformula", "HEAD_ACADEMIC");
        MockMultipartFile file = docxFile("mau-loi.docx", "Sai cú pháp: [[READING]+[LISTENING]");

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Mẫu lỗi")
                        .param("templateType", "GRADE_REPORT")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_deniedForRoleWithoutReportTemplateCreate_returns403() throws Exception {
        User staff = userWithRole("staff.noaccess.report", "STAFF");
        MockMultipartFile file = docxFile("mau.docx", "[STUDENT_NAME]");

        mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Phiếu kết quả lộ trình")
                        .param("templateType", "TRANSCRIPT")
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateFieldMappings_UC67_MainFlow_savesMappingForDetectedField() throws Exception {
        User headAcademic = userWithRole("head.academic.mapping", "HEAD_ACADEMIC");
        Long templateId = createTemplateAndGetId(headAcademic, "[STUDENT_NAME]");

        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(
                List.of(new FieldMappingItemRequest("[STUDENT_NAME]", "student.user.fullName", "Ho ten hoc sinh")));

        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldMappings", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.fieldMappings[0].placeholderKey").value("[STUDENT_NAME]"))
                .andExpect(jsonPath("$.fieldMappings[0].fieldType").value("FIELD"))
                .andExpect(jsonPath("$.fieldMappings[0].dataPath").value("student.user.fullName"));
    }

    @Test
    void updateFieldMappings_UC67_boSung_rejectsPlaceholderNotDetectedInTemplate_returns400() throws Exception {
        User headAcademic = userWithRole("head.academic.badmapping", "HEAD_ACADEMIC");
        Long templateId = createTemplateAndGetId(headAcademic, "[STUDENT_NAME]");

        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(
                List.of(new FieldMappingItemRequest("[KHONG_TON_TAI]", "x.y", null)));

        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFieldMappings_UC67_boSung_rejectsFieldMappingWithoutDataPath_returns400() throws Exception {
        User headAcademic = userWithRole("head.academic.nodatapath", "HEAD_ACADEMIC");
        Long templateId = createTemplateAndGetId(headAcademic, "[STUDENT_NAME]");

        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(
                List.of(new FieldMappingItemRequest("[STUDENT_NAME]", null, null)));

        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_UC67_MainFlow_changesNameAndDescription() throws Exception {
        User headAcademic = userWithRole("head.academic.update", "HEAD_ACADEMIC");
        Long templateId = createTemplateAndGetId(headAcademic, "[STUDENT_NAME]");

        mockMvc.perform(put("/api/report-templates/" + templateId)
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateReportTemplateRequest("Ten moi", "Mo ta moi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ten moi"))
                .andExpect(jsonPath("$.description").value("Mo ta moi"));
    }

    @Test
    void delete_UC67_MainFlow_softDeletesTemplateAndHidesFromViewById() throws Exception {
        User headAcademic = userWithRole("head.academic.delete", "HEAD_ACADEMIC");
        Long templateId = createTemplateAndGetId(headAcademic, "[STUDENT_NAME]");

        mockMvc.perform(delete("/api/report-templates/" + templateId)
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/report-templates/" + templateId)
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_UC67_MainFlow_filtersByTemplateType() throws Exception {
        User headAcademic = userWithRole("head.academic.list", "HEAD_ACADEMIC");
        createTemplateAndGetId(headAcademic, "[STUDENT_NAME]");

        mockMvc.perform(get("/api/report-templates").param("templateType", "TRANSCRIPT")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].templateType").value("TRANSCRIPT"));
    }

    private Long createTemplateAndGetId(User actor, String bodyText) throws Exception {
        MockMultipartFile file = docxFile("mau.docx", bodyText);
        String response = mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Phiếu kết quả lộ trình")
                        .param("templateType", "TRANSCRIPT")
                        .header("Authorization", bearerToken(actor, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private MockMultipartFile docxFile(String filename, String bodyText) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(bodyText);
            document.write(out);
            return new MockMultipartFile("file", filename, DOCX_CONTENT_TYPE, out.toByteArray());
        }
    }
}
