package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.support.AbstractControllerTest;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-40: xác nhận lms.exercise.manage (Hybrid PBAC — V28) chặn/cho phép
 * đúng qua HTTP thật cho cả 2 Controller dùng chung permission này
 * (QuestionBankController + ExerciseController).
 */
@Transactional
class QuestionBankControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBank_deniedForRoleWithoutLmsExerciseManage_returns403() throws Exception {
        var staff = userWithRole("staff.noaccess", "STAFF");

        mockMvc.perform(post("/api/question-banks")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateQuestionBankRequest("QB-" + SEQ.incrementAndGet(), "Ngân hàng test", null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void createBank_allowedForTeacher_returns200() throws Exception {
        var teacher = userWithRole("teacher.access", "TEACHER");

        mockMvc.perform(post("/api/question-banks")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateQuestionBankRequest("QB-" + SEQ.incrementAndGet(), "Ngân hàng test", null, null, null))))
                .andExpect(status().isOk());
    }

    @Test
    void createExercise_deniedForRoleWithoutLmsExerciseManage_returns403() throws Exception {
        var staff = userWithRole("staff.noaccess2", "STAFF");

        mockMvc.perform(post("/api/exercises")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateExerciseRequest("EX-" + SEQ.incrementAndGet(), "Đề test", null, null,
                                        "SELF_PRACTICE", new BigDecimal("100"), null, false, null, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void createExercise_allowedForTeacher_returns200() throws Exception {
        var teacher = userWithRole("teacher.access2", "TEACHER");

        mockMvc.perform(post("/api/exercises")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateExerciseRequest("EX-" + SEQ.incrementAndGet(), "Đề test", null, null,
                                        "SELF_PRACTICE", new BigDecimal("100"), null, false, null, true))))
                .andExpect(status().isOk());
    }
}
