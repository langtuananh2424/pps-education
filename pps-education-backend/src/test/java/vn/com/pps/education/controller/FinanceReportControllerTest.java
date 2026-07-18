package vn.com.pps.education.controller;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-32: xác nhận finance.report.view (Hybrid PBAC — V28) chặn/cho phép
 * đúng qua HTTP thật, thay cho test Service-level cũ gọi thẳng
 * getChainReport_rejectsWhenActorNotExecutive (đã xoá khỏi FinanceReportServiceTest).
 */
@Transactional
class FinanceReportControllerTest extends AbstractControllerTest {

    @Test
    void getChainReport_deniedForRoleWithoutFinanceReportView_returns403() throws Exception {
        var siteManager = userWithRole("sitemanager.noaccess", "SITE_MANAGER");

        mockMvc.perform(get("/api/finance/reports/chain")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .header("Authorization", bearerToken(siteManager, "SITE_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void getChainReport_allowedForExecutive_returns200() throws Exception {
        var executive = userWithRole("executive.access", "EXECUTIVE");

        mockMvc.perform(get("/api/finance/reports/chain")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .header("Authorization", bearerToken(executive, "EXECUTIVE")))
                .andExpect(status().isOk());
    }
}
