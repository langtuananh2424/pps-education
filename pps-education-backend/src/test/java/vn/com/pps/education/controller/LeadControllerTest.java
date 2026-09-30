package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.AssignLeadRequest;
import vn.com.pps.education.dto.CreateLeadRequest;
import vn.com.pps.education.dto.LeadResponse;
import vn.com.pps.education.service.LeadService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-33/34: xác nhận crm.lead.create/update/convert/crm.lead.assign
 * (Hybrid PBAC — V28, crm.lead.* tách từ crm.lead.manage ở V62) chặn/cho
 * phép đúng qua HTTP thật — permission assign khác nhóm create/update/
 * convert vì createLead (STAFF only) và assignLead (STAFF+SITE_MANAGER)
 * có tập role khác nhau.
 */
@Transactional
class LeadControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeadService leadService;

    @Test
    void createLead_deniedForRoleWithoutCrmLeadManage_returns403() throws Exception {
        var siteManager = userWithRole("sitemanager.noaccess", "SITE_MANAGER");

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", bearerToken(siteManager, "SITE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLeadRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    /** V202 (Q6): quyền CRM chuyển từ Nhân viên (STAFF) sang Tư vấn tuyển sinh (CONSULTANT). */
    @Test
    void createLead_allowedForConsultant_returns200() throws Exception {
        var consultant = userWithRole("consultant.access", "CONSULTANT");

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", bearerToken(consultant, "CONSULTANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLeadRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void assignLead_deniedForRoleWithoutCrmLeadAssign_returns403() throws Exception {
        var staff = userWithRole("staff.forlead", "STAFF");
        LeadResponse lead = leadService.createLead(newLeadRequest(), staff.getId());
        var teacher = userWithRole("teacher.noaccess", "TEACHER");

        mockMvc.perform(put("/api/leads/" + lead.id() + "/assign")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLeadRequest(staff.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    /**
     * V202 (đã xác nhận với người dùng 2026-09-30): quyền mặc định theo sidebar đã chốt — mục Khách hàng tiềm
     * năng thuộc Quản lý vận hành, không còn trong sidebar của Quản lý điểm trường.
     */
    @Test
    void assignLead_allowedForOpsManager_returns200() throws Exception {
        var staff = userWithRole("staff.forassign", "STAFF");
        LeadResponse lead = leadService.createLead(newLeadRequest(), staff.getId());
        var opsManager = userWithRole("opsmanager.access", "OPS_MANAGER");

        mockMvc.perform(put("/api/leads/" + lead.id() + "/assign")
                        .header("Authorization", bearerToken(opsManager, "OPS_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLeadRequest(staff.getId()))))
                .andExpect(status().isOk());
    }

    @Test
    void assignLead_deniedForSiteManagerAfterV202_returns403() throws Exception {
        var staff = userWithRole("staff.forassign2", "STAFF");
        LeadResponse lead = leadService.createLead(newLeadRequest(), staff.getId());
        var siteManager = userWithRole("sitemanager.noassign", "SITE_MANAGER");

        mockMvc.perform(put("/api/leads/" + lead.id() + "/assign")
                        .header("Authorization", bearerToken(siteManager, "SITE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLeadRequest(staff.getId()))))
                .andExpect(status().isForbidden());
    }

    private CreateLeadRequest newLeadRequest() {
        return new CreateLeadRequest(
                "Chị Lan", "09" + (100000000 + SEQ.incrementAndGet()), "lan" + SEQ.get() + "@example.com", "MOTHER",
                "Bé Minh", LocalDate.of(2015, 3, 20), "Lớp 3", "Tiểu học ABC",
                "WEBSITE", null, null, "Quan tâm khóa tiếng Anh giao tiếp");
    }
}
