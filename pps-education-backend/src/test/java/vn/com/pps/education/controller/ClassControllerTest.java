package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.service.CurriculumService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-18: xác nhận academic.class.manage (Hybrid PBAC — V28) chặn/cho phép
 * đúng qua HTTP thật, thay cho test Service-level cũ gọi thẳng
 * requireAuthorized() (đã xoá khỏi ClassService — xem GlobalExceptionHandler
 * cho format lỗi AuthorizationDeniedException).
 */
@Transactional
class ClassControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private SiteRepository siteRepository;

    private Long activeCurriculumId;
    private Long siteId;

    @BeforeEach
    void setUp() {
        var headAcademic = userWithRole("head.academic", "HEAD_ACADEMIC");
        var curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null),
                headAcademic.getId());
        activeCurriculumId = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId()).id();

        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        siteId = siteRepository.save(site).getId();
    }

    @Test
    void create_deniedForRoleWithoutAcademicClassManage_returns403() throws Exception {
        var teacher = userWithRole("teacher.noaccess", "TEACHER");

        mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newClassRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void create_allowedForStaff_returns200() throws Exception {
        var staff = userWithRole("staff.access", "STAFF");

        mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerToken(staff, "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newClassRequest())))
                .andExpect(status().isOk());
    }

    private CreateClassRequest newClassRequest() {
        return new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "Lớp test", siteId, activeCurriculumId,
                "OPEN", 20, 5, LocalDate.now(), null, "2026", "1");
    }
}
