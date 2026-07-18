package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.service.CurriculumService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-19 (cấu hình sổ điểm): xác nhận academic.grade.manage (Hybrid PBAC —
 * V28) chặn/cho phép đúng qua HTTP thật, thay cho test Service-level cũ.
 */
@Transactional
class GradeControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CurriculumService curriculumService;

    private Long activeCurriculumId;

    @BeforeEach
    void setUp() {
        var headAcademic = userWithRole("head.academic", "HEAD_ACADEMIC");
        var curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null),
                headAcademic.getId());
        activeCurriculumId = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId()).id();
    }

    @Test
    void createGradePeriod_deniedForRoleWithoutAcademicGradeManage_returns403() throws Exception {
        var teacher = userWithRole("teacher.noaccess", "TEACHER");

        mockMvc.perform(post("/api/curriculums/" + activeCurriculumId + "/grade-periods")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void createGradePeriod_allowedForHeadAcademic_returns200() throws Exception {
        var headAcademic = userWithRole("head.academic.access", "HEAD_ACADEMIC");

        mockMvc.perform(post("/api/curriculums/" + activeCurriculumId + "/grade-periods")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null))))
                .andExpect(status().isOk());
    }
}
