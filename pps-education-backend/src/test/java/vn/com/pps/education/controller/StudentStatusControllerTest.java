package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.UpdateStudentStatusRequest;
import vn.com.pps.education.service.StudentService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-14: xác nhận student.status.manage (Hybrid PBAC — V28) chặn/cho phép
 * đúng qua HTTP thật, thay cho test Service-level cũ gọi thẳng
 * updateStatus_rejectsActorWithoutAuthorizedRole (đã xoá khỏi StudentStatusServiceTest).
 */
@Transactional
class StudentStatusControllerTest extends AbstractControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentService studentService;

    private Long studentId;

    @BeforeEach
    void setUp() {
        var siteManager = userWithRole("site.manager.setup", "SITE_MANAGER");
        User studentUser = userWithRole("student.forstatus", "STUDENT");
        studentId = studentService.create(
                new CreateStudentRequest(studentUser.getId(), null, "HS" + (System.nanoTime() % 1_000_000), LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2026, 1, 1), null),
                siteManager.getId()).id();
    }

    @Test
    void updateStatus_deniedForRoleWithoutStudentStatusManage_returns403() throws Exception {
        var teacher = userWithRole("teacher.noaccess", "TEACHER");

        mockMvc.perform(post("/api/students/" + studentId + "/status")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateStudentStatusRequest("SUSPENDED", "Vi phạm nội quy", LocalDate.now()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void updateStatus_allowedForStaff_returns200() throws Exception {
        var staff = userWithRole("staff.access", "STAFF");

        mockMvc.perform(post("/api/students/" + studentId + "/status")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateStudentStatusRequest("SUSPENDED", "Vi phạm nội quy", LocalDate.now()))))
                .andExpect(status().isOk());
    }
}
