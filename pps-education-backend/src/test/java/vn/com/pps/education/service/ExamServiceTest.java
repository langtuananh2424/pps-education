package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateExamRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-40: Kho đề — CRUD "Đề" (Exam) + gán/gỡ lớp (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30). */
@Transactional
class ExamServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ExamService examService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExamQuestionService examQuestionService;

    @Autowired
    private QuestionBankService questionBankService;

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

    private User headAcademic;
    private User teacher;
    private CurriculumResponse curriculumA;
    private CurriculumResponse curriculumB;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");

        CurriculumResponse rawA = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn A", "MAIN", null, null, null), headAcademic.getId());
        curriculumA = curriculumService.update(rawA.id(),
                new UpdateCurriculumRequest("Chuẩn A", null, null, null, "ACTIVE", false), headAcademic.getId());
        CurriculumResponse rawB = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn B", "MAIN", null, null, null), headAcademic.getId());
        curriculumB = curriculumService.update(rawB.id(),
                new UpdateCurriculumRequest("Chuẩn B", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), curriculumA.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());
    }

    @Test
    void createExam_UC40_MainFlow_savesWithCurriculumForFiltering() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        assertThat(exam.title()).isEqualTo("IELTS Grade 6");
        assertThat(exam.curriculumId()).isEqualTo(curriculumA.id());
        assertThat(exam.curriculumCode()).isEqualTo(curriculumA.code());
    }

    /** V74, đã xác nhận với người dùng 2026-08-04: teacherType/examType bắt buộc chọn 1 trong 2, lưu đúng vào Đề. */
    @Test
    void createExam_boSung_savesTeacherTypeAndExamType() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "FOREIGN", "REVIEW"), teacher.getId());

        assertThat(exam.teacherType()).isEqualTo("FOREIGN");
        assertThat(exam.examType()).isEqualTo("REVIEW");
    }

    @Test
    void createExam_boSung_rejectsInvalidTeacherType() {
        assertThatThrownBy(() -> examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "KHONG_HOP_LE", "HOMEWORK"), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createExam_boSung_rejectsInvalidExamType() {
        assertThatThrownBy(() -> examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "KHONG_HOP_LE"), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateExam_UC40_MainFlow_changesTitleOnly() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "Tên cũ", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        ExamResponse updated = examService.updateExam(exam.id(),
                new UpdateExamRequest("Tên mới", "VIETNAMESE", "HOMEWORK"), teacher.getId());

        assertThat(updated.title()).isEqualTo("Tên mới");
        assertThat(updated.curriculumId()).isEqualTo(curriculumA.id());
    }

    /** V74, đã xác nhận với người dùng 2026-08-04: teacherType/examType sửa được cùng lúc với title. */
    @Test
    void updateExam_boSung_changesTeacherTypeAndExamTypeAlongWithTitle() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "Tên cũ", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        ExamResponse updated = examService.updateExam(exam.id(),
                new UpdateExamRequest("Tên mới", "FOREIGN", "REVIEW"), teacher.getId());

        assertThat(updated.teacherType()).isEqualTo("FOREIGN");
        assertThat(updated.examType()).isEqualTo("REVIEW");
    }

    @Test
    void listExams_UC40_MainFlow_filtersByCurriculum() {
        ExamResponse examA = examService.createExam(
                new CreateExamRequest(examCode(), "Đề khung A", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        ExamResponse examB = examService.createExam(
                new CreateExamRequest(examCode(), "Đề khung B", curriculumB.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        List<ExamResponse> filtered = examService.listExams(curriculumA.id(), null, teacher.getId());

        assertThat(filtered).extracting(ExamResponse::id).contains(examA.id()).doesNotContain(examB.id());
    }

    @Test
    void listExams_boSung_returnsAllWhenCurriculumFilterOmitted() {
        ExamResponse examA = examService.createExam(
                new CreateExamRequest(examCode(), "Đề khung A", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        ExamResponse examB = examService.createExam(
                new CreateExamRequest(examCode(), "Đề khung B", curriculumB.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        List<ExamResponse> all = examService.listExams(null, null, teacher.getId());

        assertThat(all).extracting(ExamResponse::id).contains(examA.id(), examB.id());
    }

    /** V74, đã xác nhận với người dùng 2026-08-04: lọc theo GV Việt Nam/nước ngoài để phục vụ giao bài. */
    @Test
    void listExams_boSung_filtersByTeacherType() {
        ExamResponse examVn = examService.createExam(
                new CreateExamRequest(examCode(), "Đề GV Việt Nam", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        ExamResponse examForeign = examService.createExam(
                new CreateExamRequest(examCode(), "Đề GV nước ngoài", curriculumA.id(), "FOREIGN", "HOMEWORK"), teacher.getId());

        List<ExamResponse> filtered = examService.listExams(null, "FOREIGN", teacher.getId());

        assertThat(filtered).extracting(ExamResponse::id).contains(examForeign.id()).doesNotContain(examVn.id());
    }

    @Test
    void assignToClass_UC40_MainFlow_addsClassToExam() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());

        assertThat(examService.listAssignedClasses(exam.id(), teacher.getId()))
                .extracting(ClassResponse::id).containsExactly(schoolClass.id());
    }

    @Test
    void assignToClass_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        assertThatThrownBy(() -> examService.assignToClass(exam.id(), schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    /** "Một đề sẽ có thể gán được cho nhiều lớp" — gán lại lớp đã gán rồi không lỗi, không tạo dòng trùng. */
    @Test
    void assignToClass_boSung_isIdempotentWhenAlreadyAssigned() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());

        assertThat(examService.listAssignedClasses(exam.id(), teacher.getId())).hasSize(1);
    }

    @Test
    void unassignFromClass_UC40_MainFlow_removesClassFromExam() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());

        examService.unassignFromClass(exam.id(), schoolClass.id(), teacher.getId());

        assertThat(examService.listAssignedClasses(exam.id(), teacher.getId())).isEmpty();
    }

    @Test
    void listExercises_UC40_MainFlow_returnsBaiThuocDe() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập) — phải qua
        // ExamQuestionService#createQuestion (tự resolve bank nội bộ theo examId).
        QuestionResponse mc = examQuestionService.createQuestion(exam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Unit 1", exam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());

        List<ExerciseResponse> exercises = examService.listExercises(exam.id(), teacher.getId());

        assertThat(exercises).extracting(ExerciseResponse::id).containsExactly(exercise.id());
        assertThat(exercises.get(0).examCode()).isEqualTo(exam.code());
    }

    /** V80 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — "Xóa Đề": soft-delete, ẩn khỏi getExam/listExams. */
    @Test
    void deleteExam_MainFlow_softDeletesAndHidesFromListing() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        examService.deleteExam(exam.id(), teacher.getId());

        assertThatThrownBy(() -> examService.getExam(exam.id(), teacher.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(examService.listExams(null, null, teacher.getId())).extracting(ExamResponse::id).doesNotContain(exam.id());
    }

    @Test
    void deleteExam_A_rejectsWhenExamHasActiveExercise() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Unit 1", exam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, null, true), teacher.getId());

        assertThatThrownBy(() -> examService.deleteExam(exam.id(), teacher.getId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteExam_MainFlow_allowsWhenAllExercisesArchived() {
        ExamResponse exam = examService.createExam(
                new CreateExamRequest(examCode(), "IELTS Grade 6", curriculumA.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Unit 1", exam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, null, true), teacher.getId());
        exerciseService.deleteExercise(exercise.id(), teacher.getId());

        examService.deleteExam(exam.id(), teacher.getId());

        assertThatThrownBy(() -> examService.getExam(exam.id(), teacher.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String examCode() {
        return "KD-" + SEQ.incrementAndGet();
    }

    private String exerciseCode() {
        return "EX-" + SEQ.incrementAndGet();
    }

    private String bankCode() {
        return "QB-" + SEQ.incrementAndGet();
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
