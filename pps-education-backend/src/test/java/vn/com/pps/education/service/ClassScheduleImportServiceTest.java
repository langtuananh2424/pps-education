package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassScheduleImportResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.repository.ImportJobRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-57: Nhập lịch học qua Excel — Main Flow (tạo đúng buổi cho dòng hợp
 * lệ), A2 (dòng thiếu GV bị bỏ qua, ghi lỗi, không chặn dòng khác), A3
 * (file sai định dạng → FAILED ngay). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class ClassScheduleImportServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private ClassScheduleImportService classScheduleImportService;

    @Autowired
    private ClassSessionService classSessionService;

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
    private RoomRepository roomRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private Room room;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        room = newRoom(site);
    }

    @Test
    void importSchedule_UC57_MainFlow_createsSessionsForValidRows() throws IOException {
        LocalDate date1 = LocalDate.now().plusDays(10);
        LocalDate date2 = LocalDate.now().plusDays(11);
        byte[] file = buildWorkbook(
                new String[]{"Ngay", "Gio bat dau", "Gio ket thuc", "Loai buoi", "Username GV", "Ma phong"},
                new String[][]{
                        {date1.format(DATE_FORMAT), "08:00", "09:40", "", teacher.getUsername(), room.getCode()},
                        {date2.format(DATE_FORMAT), "10:00", "11:40", "MAKEUP", teacher.getUsername(), ""},
                });

        ClassScheduleImportResponse result = classScheduleImportService.importSchedule(schoolClass.id(),
                new MockMultipartFile("file", "lich.xlsx", "application/vnd.openxmlformats", file), headAcademic.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(0);

        List<ClassSessionResponse> sessions = classSessionService.listSessions(schoolClass.id(), headAcademic.getId());
        assertThat(sessions).hasSize(2);
        ClassSessionResponse created1 = sessions.stream().filter(s -> s.sessionDate().equals(date1)).findFirst().orElseThrow();
        assertThat(created1.roomId()).isEqualTo(room.getId());
        assertThat(created1.sessionType()).isEqualTo("REGULAR");
        ClassSessionResponse created2 = sessions.stream().filter(s -> s.sessionDate().equals(date2)).findFirst().orElseThrow();
        assertThat(created2.sessionType()).isEqualTo("MAKEUP");
        assertThat(created2.roomId()).isNull();
    }

    @Test
    void importSchedule_UC57_A2_rowMissingTeacherUsernameDoesNotBlockOthers() throws IOException {
        LocalDate date1 = LocalDate.now().plusDays(15);
        LocalDate date2 = LocalDate.now().plusDays(16);
        byte[] file = buildWorkbook(
                new String[]{"Ngay", "Gio bat dau", "Gio ket thuc", "Loai buoi", "Username GV", "Ma phong"},
                new String[][]{
                        {date1.format(DATE_FORMAT), "08:00", "09:40", "", "", ""},
                        {date2.format(DATE_FORMAT), "08:00", "09:40", "", teacher.getUsername(), ""},
                });

        ClassScheduleImportResponse result = classScheduleImportService.importSchedule(schoolClass.id(),
                new MockMultipartFile("file", "lich.xlsx", "application/vnd.openxmlformats", file), headAcademic.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary()).hasSize(1);

        List<ClassSessionResponse> sessions = classSessionService.listSessions(schoolClass.id(), headAcademic.getId());
        assertThat(sessions).extracting(ClassSessionResponse::sessionDate).containsExactly(date2);
    }

    @Test
    void importSchedule_UC57_A3_marksFailedForCorruptFile() {
        byte[] garbage = "not an excel file".getBytes();

        ClassScheduleImportResponse result = classScheduleImportService.importSchedule(schoolClass.id(),
                new MockMultipartFile("file", "broken.xlsx", "application/vnd.openxmlformats", garbage), headAcademic.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void getJob_UC57_returnsSameResultAsImport() throws IOException {
        LocalDate date1 = LocalDate.now().plusDays(25);
        byte[] file = buildWorkbook(
                new String[]{"Ngay", "Gio bat dau", "Gio ket thuc", "Loai buoi", "Username GV", "Ma phong"},
                new String[][]{
                        {date1.format(DATE_FORMAT), "08:00", "09:40", "", teacher.getUsername(), ""},
                });

        ClassScheduleImportResponse imported = classScheduleImportService.importSchedule(schoolClass.id(),
                new MockMultipartFile("file", "lich.xlsx", "application/vnd.openxmlformats", file), headAcademic.getId());

        ClassScheduleImportResponse fetched = classScheduleImportService.getJob(imported.id());

        assertThat(fetched.id()).isEqualTo(imported.id());
        assertThat(fetched.status()).isEqualTo("COMPLETED");
    }

    private byte[] buildWorkbook(String[] headers, String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Lich hoc");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String curriculumCode() {
        return "CUR-SCH-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-SCH-" + SEQ.incrementAndGet();
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
        s.setCode("SITE-SCH-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private Room newRoom(Site site) {
        Room r = new Room();
        r.setSite(site);
        r.setCode("ROOM-SCH-" + SEQ.incrementAndGet());
        r.setName("Test Room");
        r.setRoomType(Room.RoomType.THEORY);
        r.setCapacity(30);
        r.setFlexible(false);
        return roomRepository.save(r);
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
