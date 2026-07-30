package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.dto.CreateRoomRequest;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.support.AbstractControllerTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-37: xác nhận facility.room.create/update (Hybrid PBAC — V28, tách từ
 * facility.room.manage ở V62) chặn/cho phép đúng qua HTTP thật, thay cho
 * test Service-level cũ gọi thẳng
 * createRoom_rejectsWhenActorNotAuthorized (đã xoá khỏi RoomServiceTest).
 */
@Transactional
class RoomControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

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
    void createRoom_deniedForRoleWithoutFacilityRoomManage_returns403() throws Exception {
        var parent = userWithRole("parent.noaccess", "PARENT");

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerToken(parent, "PARENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest(siteId, "P" + SEQ.incrementAndGet(), "Phòng test", "THEORY", 30, false, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void createRoom_allowedForStaff_returns200() throws Exception {
        var staff = userWithRole("staff.access", "STAFF");

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest(siteId, "P" + SEQ.incrementAndGet(), "Phòng test", "THEORY", 30, false, true))))
                .andExpect(status().isOk());
    }
}
