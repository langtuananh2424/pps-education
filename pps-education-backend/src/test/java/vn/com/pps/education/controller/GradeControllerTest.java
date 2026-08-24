package vn.com.pps.education.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.service.ClassService;
import vn.com.pps.education.service.CurriculumService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-19 (cấu hình sổ điểm): xác nhận academic.grade.setup.create (Hybrid
 * PBAC — V28/V46, đổi tên từ academic.grade.period.create ở V95) chặn/cho
 * phép đúng qua HTTP thật, thay cho test Service-level cũ. V95 (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng): endpoint đổi từ
 * /api/curriculums/{id}/grade-periods sang /api/classes/{id}/grade-component-setups
 * (gắn lớp + kỳ học thay vì curriculum).
 */
@Transactional
class GradeControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private ClassService classService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    private Long classId;
    private Long academicTermId;

    @BeforeEach
    void setUp() {
        var headAcademic = userWithRole("head.academic", "HEAD_ACADEMIC");
        var curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null, null, null),
                headAcademic.getId());
        Long activeCurriculumId = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId()).id();

        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        site = siteRepository.save(site);

        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
        term.setCode("TERM-" + SEQ.incrementAndGet());
        term.setName("Kỳ test");
        term.setStartDate(LocalDate.now().minusMonths(1));
        term.setEndDate(LocalDate.now().plusMonths(2));
        term.setCreatedBy(headAcademic);
        academicTermId = academicTermRepository.save(term).getId();

        ClassResponse schoolClass = classService.create(
                new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "8A2", site.getId(), activeCurriculumId, "OPEN", 20,
                        null, LocalDate.now(), null, null), headAcademic.getId());
        classId = schoolClass.id();
    }

    @Test
    void createGradeComponentSetup_deniedForRoleWithoutAcademicGradeSetupCreate_returns403() throws Exception {
        var teacher = userWithRole("teacher.noaccess", "TEACHER");

        mockMvc.perform(post("/api/classes/" + classId + "/grade-component-setups")
                        .header("Authorization", bearerToken(teacher, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGradeComponentSetupRequest(academicTermId, "MID_TERM", "POINT_10", LocalDate.now(), false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void createGradeComponentSetup_allowedForHeadAcademic_returns200() throws Exception {
        var headAcademic = userWithRole("head.academic.access", "HEAD_ACADEMIC");

        mockMvc.perform(post("/api/classes/" + classId + "/grade-component-setups")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGradeComponentSetupRequest(academicTermId, "MID_TERM", "POINT_10", LocalDate.now(), false))))
                .andExpect(status().isOk());
    }
}
