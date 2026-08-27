package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.support.AbstractControllerTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-16: xác nhận academic.curriculum.create/update (Hybrid PBAC — V28,
 * tách từ academic.curriculum.manage ở V62) chặn/cho phép đúng qua HTTP
 * thật, thay cho test Service-level cũ gọi thẳng requireHeadAcademic()
 * (đã xoá khỏi CurriculumService).
 */
@Transactional
class CurriculumControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void create_deniedForRoleWithoutAcademicCurriculumManage_returns403() throws Exception {
        var staff = userWithRole("staff.noaccess", "STAFF");

        mockMvc.perform(post("/api/curriculums")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCurriculumRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void create_allowedForHeadAcademic_returns200() throws Exception {
        var headAcademic = userWithRole("head.academic.access", "HEAD_ACADEMIC");

        mockMvc.perform(post("/api/curriculums")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCurriculumRequest())))
                .andExpect(status().isOk());
    }

    private CreateCurriculumRequest newCurriculumRequest() {
        return new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null, null, null);
    }
}
