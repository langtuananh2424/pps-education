package vn.com.pps.education.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import vn.com.pps.education.academic.domain.AttendanceMark;
import vn.com.pps.education.academic.domain.AttendanceSession;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.permission.domain.Role;
import vn.com.pps.education.facility.domain.Site;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.student.domain.StudentComment;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.permission.domain.UserRole;
import vn.com.pps.education.academic.dto.AssignTeacherRequest;
import vn.com.pps.education.lms.dto.ClassResponse;
import vn.com.pps.education.academic.dto.CreateClassRequest;
import vn.com.pps.education.academic.dto.CreateCurriculumRequest;
import vn.com.pps.education.academic.dto.CurriculumResponse;
import vn.com.pps.education.academic.dto.EnrollStudentRequest;
import vn.com.pps.education.report.dto.FieldMappingItemRequest;
import vn.com.pps.education.report.dto.GenerateReportRequest;
import vn.com.pps.education.academic.dto.UpdateCurriculumRequest;
import vn.com.pps.education.report.dto.UpdateFieldMappingsRequest;
import vn.com.pps.education.academic.repository.AttendanceMarkRepository;
import vn.com.pps.education.academic.repository.AttendanceSessionRepository;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.permission.repository.RoleRepository;
import vn.com.pps.education.academic.repository.SchoolClassRepository;
import vn.com.pps.education.facility.repository.SiteRepository;
import vn.com.pps.education.student.repository.StudentCommentRepository;
import vn.com.pps.education.student.repository.StudentRepository;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.permission.repository.UserRoleRepository;
import vn.com.pps.education.academic.service.ClassService;
import vn.com.pps.education.academic.service.CurriculumService;
import vn.com.pps.education.support.AbstractControllerTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-68: Xuất báo cáo ngày (DAILY_REPORT, scope CLASS_SESSION) — xác nhận
 * bảng động {@code [[TABLE:STUDENTS]]} nhân bản đúng số dòng theo học
 * sinh ACTIVE của lớp, sắp theo tên A-Z (đã xác nhận với người dùng), và
 * điền đúng trạng thái điểm danh + nhận xét ngày của từng học sinh.
 */
@Transactional
class DailyReportGenerationControllerTest extends AbstractControllerTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String PUBLIC_BASE_URL = "https://media.pps.edu.vn";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @MockBean
    private S3Client r2Client;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceMarkRepository attendanceMarkRepository;

    @Autowired
    private StudentCommentRepository studentCommentRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private byte[] uploadedTemplateBytes;

    @DynamicPropertySource
    static void mediaR2Config(DynamicPropertyRegistry registry) {
        registry.add("app.media.r2.bucket", () -> "test-bucket");
        registry.add("app.media.r2.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @BeforeEach
    void setUp() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(r2Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation ->
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(uploadedTemplateBytes))));

        headAcademic = newUser("head.academic.daily");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "7A2", site.getId(), activeCurriculum.id(), "OPEN", 20,
                        null, LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher.daily");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());
    }

    @Test
    void generate_UC68_MainFlow_clonesStudentsTableSortedByNameWithAttendanceAndComment() throws Exception {
        Student binh = enrollStudent("Bình");
        Student an = enrollStudent("An");
        Student cuong = enrollStudent("Cường");
        Student dung = enrollStudent("Dũng");
        enrollStudent("Em");

        ClassSession session = newClassSession();
        User assistant = newUser("assistant.daily");
        assistant.setFullName("Trợ Giảng Test");
        userRepository.save(assistant);
        session.setAssistantTeacher(assistant);
        classSessionRepository.save(session);

        AttendanceSession attendanceSession = newAttendanceSession(session);
        markAttendance(attendanceSession, an, AttendanceMark.Status.PRESENT);
        markAttendance(attendanceSession, binh, AttendanceMark.Status.ABSENT);
        markAttendance(attendanceSession, cuong, AttendanceMark.Status.EXCUSED);
        markAttendance(attendanceSession, dung, AttendanceMark.Status.LATE);
        StudentComment anComment = writeDailyComment(session, an, "Con học tốt hôm nay.");
        // GV nhập tay "Chưa làm bài" ở cột BTVN buổi trước (nhập tay thắng fallback tự động).
        anComment.setHomeworkPreviousScore("Chưa làm bài");
        anComment.setHomeworkNext("Unit 8 trang 1 & 2");
        studentCommentRepository.save(anComment);
        StudentComment dungComment = writeDailyComment(session, dung, "");
        dungComment.setHomeworkPreviousScore("80%");
        dungComment.setHomeworkNext("Unit 8 trang 1 & 2"); // trùng với An -> chỉ in 1 lần
        dungComment.setHomeworkNextReading("Reading trang 5");
        studentCommentRepository.save(dungComment);

        Long templateId = createDailyReportTemplate();
        configureAllHeaderFieldMappings(templateId);

        GenerateReportRequest request = new GenerateReportRequest(templateId, "CLASS_SESSION", null, null, List.of(), session.getId());

        byte[] mergedDocx = generateAndCaptureBytes(request);

        // Vắng tính cả Vắng có phép (Bình + Cường); Có mặt tính cả Đi trễ (An + Dũng); "Em" chưa điểm danh không nằm trong 2 con số.
        String headerText = extractFirstParagraphText(mergedDocx);
        assertThat(headerText).isEqualTo("Lớp: 7A2 - Có mặt: 2/5 - Vắng: 2 - Trợ giảng: Trợ Giảng Test");
        assertThat(extractParagraphText(mergedDocx, 1))
                .isEqualTo("HS vắng: Bình Test, Cường Test | Thiếu BTVN: An Test");
        // HOMEWORK_CONTENT nhiều dòng -> <w:br/> trong cùng đoạn văn (POI đọc lại thành "\n").
        assertThat(extractParagraphText(mergedDocx, 2))
                .isEqualTo("BTVN: - Unit 8 trang 1 & 2\n- Reading: Reading trang 5");

        List<List<String>> tableRows = extractTableRows(mergedDocx);
        // Dòng 0 là header cột (ngoài marker) -> các dòng sau là dữ liệu học sinh, sắp A-Z.
        assertThat(tableRows).containsExactly(
                List.of("Ten", "Diem danh", "Nhan xet"),
                List.of("An Test", "Có mặt", "Con học tốt hôm nay."),
                List.of("Bình Test", "Vắng", ""),
                List.of("Cường Test", "Vắng có phép", ""),
                List.of("Dũng Test", "Đi trễ", ""),
                List.of("Em Test", "Chưa điểm danh", ""));
    }

    @Test
    void generate_UC68_MainFlow_leavesOptionalFieldsBlankWhenNothingToShow() throws Exception {
        Student an = enrollStudent("An");

        ClassSession session = newClassSession();
        AttendanceSession attendanceSession = newAttendanceSession(session);
        markAttendance(attendanceSession, an, AttendanceMark.Status.PRESENT);

        Long templateId = createDailyReportTemplate();
        configureAllHeaderFieldMappings(templateId);

        GenerateReportRequest request = new GenerateReportRequest(templateId, "CLASS_SESSION", null, null, List.of(), session.getId());

        byte[] mergedDocx = generateAndCaptureBytes(request);

        assertThat(extractFirstParagraphText(mergedDocx))
                .isEqualTo("Lớp: 7A2 - Có mặt: 1/1 - Vắng: 0 - Trợ giảng: ");
        // Không ai vắng/thiếu BTVN, chưa giao BTVN -> để rỗng, không chặn xuất.
        assertThat(extractParagraphText(mergedDocx, 1)).isEqualTo("HS vắng:  | Thiếu BTVN: ");
        assertThat(extractParagraphText(mergedDocx, 2)).isEqualTo("BTVN: ");
    }

    private void configureAllHeaderFieldMappings(Long templateId) throws Exception {
        configureFieldMappings(templateId,
                new FieldMappingItemRequest("[CLASS_NAME]", "CLASS_NAME", null),
                new FieldMappingItemRequest("[PRESENT_COUNT]", "PRESENT_COUNT", null),
                new FieldMappingItemRequest("[TOTAL_STUDENTS]", "TOTAL_STUDENTS", null),
                new FieldMappingItemRequest("[ABSENT_COUNT]", "ABSENT_COUNT", null),
                new FieldMappingItemRequest("[ASSISTANT_TEACHER_NAME]", "ASSISTANT_TEACHER_NAME", null),
                new FieldMappingItemRequest("[ABSENT_STUDENT_NAMES]", "ABSENT_STUDENT_NAMES", null),
                new FieldMappingItemRequest("[MISSING_HOMEWORK_STUDENT_NAMES]", "MISSING_HOMEWORK_STUDENT_NAMES", null),
                new FieldMappingItemRequest("[HOMEWORK_CONTENT]", "HOMEWORK_CONTENT", null),
                new FieldMappingItemRequest("[[TABLE:STUDENTS]]", null, null));
    }

    private byte[] generateAndCaptureBytes(GenerateReportRequest request) throws Exception {
        mockMvc.perform(post("/api/reports/generate")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<RequestBody> bodyCaptor = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(r2Client, atLeastOnce()).putObject(
                argThat((PutObjectRequest req) -> req.key().contains("/generated/")), bodyCaptor.capture());
        RequestBody lastBody = bodyCaptor.getAllValues().get(bodyCaptor.getAllValues().size() - 1);
        return lastBody.contentStreamProvider().newStream().readAllBytes();
    }

    private Long createDailyReportTemplate() throws Exception {
        uploadedTemplateBytes = buildDailyReportDocx();
        MockMultipartFile file = new MockMultipartFile("file", "mau.docx", DOCX_CONTENT_TYPE, uploadedTemplateBytes);
        String response = mockMvc.perform(multipart("/api/report-templates")
                        .file(file)
                        .param("name", "Bao cao ngay")
                        .param("templateType", "DAILY_REPORT")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void configureFieldMappings(Long templateId, FieldMappingItemRequest... items) throws Exception {
        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(List.of(items));
        mockMvc.perform(put("/api/report-templates/" + templateId + "/field-mappings")
                        .header("Authorization", bearerToken(headAcademic, "HEAD_ACADEMIC"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /** Đoạn văn header + bảng 4 dòng (header cột, mở marker, 1 dòng mẫu 3 cột, đóng marker). */
    private byte[] buildDailyReportDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph header = document.createParagraph();
            XWPFRun run = header.createRun();
            run.setText("Lớp: [CLASS_NAME] - Có mặt: [PRESENT_COUNT]/[TOTAL_STUDENTS] - Vắng: [ABSENT_COUNT] - Trợ giảng: [ASSISTANT_TEACHER_NAME]");

            document.createParagraph().createRun()
                    .setText("HS vắng: [ABSENT_STUDENT_NAMES] | Thiếu BTVN: [MISSING_HOMEWORK_STUDENT_NAMES]");
            document.createParagraph().createRun().setText("BTVN: [HOMEWORK_CONTENT]");

            XWPFTable table = document.createTable(4, 3);
            table.getRow(0).getCell(0).setText("Ten");
            table.getRow(0).getCell(1).setText("Diem danh");
            table.getRow(0).getCell(2).setText("Nhan xet");
            table.getRow(1).getCell(0).setText("[[TABLE:STUDENTS]]");
            table.getRow(2).getCell(0).setText("[STUDENT_NAME]");
            table.getRow(2).getCell(1).setText("[ATTENDANCE_STATUS]");
            table.getRow(2).getCell(2).setText("[STUDENT_COMMENT]");
            table.getRow(3).getCell(0).setText("[[/TABLE:STUDENTS]]");

            document.write(out);
            return out.toByteArray();
        }
    }

    private String extractFirstParagraphText(byte[] docxBytes) throws IOException {
        return extractParagraphText(docxBytes, 0);
    }

    private String extractParagraphText(byte[] docxBytes, int index) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            return document.getParagraphs().get(index).getText();
        }
    }

    private List<List<String>> extractTableRows(byte[] docxBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            XWPFTable table = document.getTables().get(0);
            return table.getRows().stream()
                    .map(row -> row.getTableCells().stream().map(XWPFTableCell::getText).toList())
                    .toList();
        }
    }

    private Student enrollStudent(String firstName) {
        User user = newUser("student.daily." + firstName.toLowerCase());
        user.setFullName(firstName + " Test");
        userRepository.save(user);
        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-DAILY-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        return student;
    }

    private ClassSession newClassSession() {
        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClassRepository.getReferenceById(schoolClass.id()));
        session.setSessionDate(LocalDate.now());
        session.setStartTime(LocalTime.of(18, 0));
        session.setEndTime(LocalTime.of(19, 30));
        session.setPrimaryTeacher(teacher);
        session.setCreatedBy(headAcademic);
        return classSessionRepository.save(session);
    }

    private AttendanceSession newAttendanceSession(ClassSession session) {
        AttendanceSession attendanceSession = new AttendanceSession();
        attendanceSession.setClassSession(session);
        attendanceSession.setMarkedBy(teacher);
        return attendanceSessionRepository.save(attendanceSession);
    }

    private void markAttendance(AttendanceSession attendanceSession, Student student, AttendanceMark.Status status) {
        AttendanceMark mark = new AttendanceMark();
        mark.setAttendanceSession(attendanceSession);
        mark.setStudent(student);
        mark.setStatus(status);
        attendanceMarkRepository.save(mark);
    }

    private StudentComment writeDailyComment(ClassSession session, Student student, String content) {
        StudentComment comment = new StudentComment();
        comment.setStudent(student);
        comment.setSchoolClass(schoolClassRepository.getReferenceById(schoolClass.id()));
        comment.setTeacher(teacher);
        comment.setCommentType(StudentComment.CommentType.DAILY);
        comment.setClassSession(session);
        comment.setCommentDate(session.getSessionDate());
        comment.setContent(content);
        return studentCommentRepository.save(comment);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
