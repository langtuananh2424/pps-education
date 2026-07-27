package vn.com.pps.education.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.support.AbstractControllerTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-36b: xác nhận facility.partner-contract.view (V51, tách từ
 * facility.manage — Hybrid PBAC V28) chặn/cho phép đúng qua HTTP thật cho
 * GET /api/sites/{siteId}/partner-contracts — endpoint này trước đây thiếu
 * @PreAuthorize (cho phép mọi user đã đăng nhập xem hợp đồng của bất kỳ
 * điểm trường nào), nay đồng nhất với 5 endpoint còn lại của
 * PartnerContractController.
 */
@Transactional
class PartnerContractControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private SiteRepository siteRepository;

    private Long siteId;

    @BeforeEach
    void setUp() {
        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        siteId = siteRepository.save(site).getId();
    }

    @Test
    void listBySite_deniedForRoleWithoutFacilityManage_returns403() throws Exception {
        var parent = userWithRole("parent.noaccess", "PARENT");

        mockMvc.perform(get("/api/sites/{siteId}/partner-contracts", siteId)
                        .header("Authorization", bearerToken(parent, "PARENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void listBySite_allowedForOpsManager_returns200() throws Exception {
        var opsManager = userWithRole("ops.access", "OPS_MANAGER");

        mockMvc.perform(get("/api/sites/{siteId}/partner-contracts", siteId)
                        .header("Authorization", bearerToken(opsManager, "OPS_MANAGER")))
                .andExpect(status().isOk());
    }
}
