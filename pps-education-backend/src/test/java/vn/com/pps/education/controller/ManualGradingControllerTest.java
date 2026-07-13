package vn.com.pps.education.controller;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-41: xác nhận lms.grading.manage (Hybrid PBAC — V28, @PreAuthorize ở
 * mức class trên ManualGradingController) chặn/cho phép đúng qua HTTP thật.
 */
@Transactional
class ManualGradingControllerTest extends AbstractControllerTest {

    @Test
    void listPendingGrading_deniedForRoleWithoutLmsGradingManage_returns403() throws Exception {
        var staff = userWithRole("staff.noaccess", "STAFF");

        mockMvc.perform(get("/api/grading/pending")
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void listPendingGrading_allowedForTeacher_returns200() throws Exception {
        var teacher = userWithRole("teacher.access", "TEACHER");

        mockMvc.perform(get("/api/grading/pending")
                        .header("Authorization", bearerToken(teacher, "TEACHER")))
                .andExpect(status().isOk());
    }
}
