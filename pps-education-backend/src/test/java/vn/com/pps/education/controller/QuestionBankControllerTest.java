package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.service.QuestionBankService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-40: xác nhận lms.question-bank.* (QuestionBankController) và
 * lms.exercise.* (ExerciseController) — Hybrid PBAC V28, tách thành 2
 * nhóm permission riêng ở V62 (2 resource khác nhau) — chặn/cho phép đúng
 * qua HTTP thật cho cả 2 Controller.
 */
@Transactional
class QuestionBankControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionBankService questionBankService;

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

    /**
     * Bổ sung: GET /api/questions/{id} trước đây không gate quyền — bất
     * kỳ ai đăng nhập (kể cả học viên) đều xem được is_correct của mọi
     * câu hỏi. Đã thêm lms.question-bank.view — xác nhận qua HTTP thật.
     */
    @Test
    void getQuestion_deniedForRoleWithoutLmsExerciseManage_returns403() throws Exception {
        var teacher = userWithRole("teacher.getq", "TEACHER");
        var bank = questionBankService.createBank(
                new CreateQuestionBankRequest("QB-" + SEQ.incrementAndGet(), "Ngân hàng test", null, null, null), teacher.getId());
        QuestionResponse question = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null, List.of()),
                teacher.getId());
        var staff = userWithRole("staff.getq", "STAFF");

        mockMvc.perform(get("/api/questions/" + question.id())
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void getQuestion_allowedForTeacher_returns200() throws Exception {
        var teacher = userWithRole("teacher.getq2", "TEACHER");
        var bank = questionBankService.createBank(
                new CreateQuestionBankRequest("QB-" + SEQ.incrementAndGet(), "Ngân hàng test", null, null, null), teacher.getId());
        QuestionResponse question = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null, List.of()),
                teacher.getId());

        mockMvc.perform(get("/api/questions/" + question.id())
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isOk());
    }
}
