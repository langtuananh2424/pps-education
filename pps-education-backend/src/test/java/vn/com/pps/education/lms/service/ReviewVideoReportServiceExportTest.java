package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.lms.domain.ReflexQuestionProgress;
import vn.com.pps.education.lms.domain.ReflexQuestionProgressHistory;
import vn.com.pps.education.lms.domain.ReviewVideoAssignment;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressHistoryRepository;
import vn.com.pps.education.lms.repository.ReflexQuestionProgressRepository;
import vn.com.pps.education.lms.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.permission.service.PermissionEvaluationService;
import vn.com.pps.education.student.domain.Student;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** File xuất Video phản xạ: lịch sử từng lần làm (kèm bài viết + phần chấm giáo viên) và kết quả chính thức hiện tại. */
@ExtendWith(MockitoExtension.class)
class ReviewVideoReportServiceExportTest {

    private static final long ASSIGNMENT_ID = 9L;
    private static final long ACTOR_ID = 5L;

    @Mock
    private ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    @Mock
    private ReflexQuestionProgressRepository reflexQuestionProgressRepository;
    @Mock
    private ReflexQuestionProgressHistoryRepository reflexQuestionProgressHistoryRepository;
    @Mock
    private PermissionEvaluationService permissionEvaluationService;

    private ReviewVideoReportService service;
    private Student student;
    private ReviewVideoQuestion question;
    private ReviewVideoAssignment assignment;

    @BeforeEach
    void setUp() {
        service = new ReviewVideoReportService(reviewVideoAssignmentRepository, null, null, null, null,
                reflexQuestionProgressRepository, reflexQuestionProgressHistoryRepository, null, null, null, null,
                permissionEvaluationService, null, null, null);
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(10L);
        assignment = new ReviewVideoAssignment();
        assignment.setSchoolClass(schoolClass);
        User studentUser = new User();
        studentUser.setFullName("Học sinh A");
        student = new Student();
        student.setId(3L);
        student.setStudentCode("HS-3");
        student.setUser(studentUser);
        question = new ReviewVideoQuestion();
        question.setId(21L);
        question.setDisplayOrder(1);
        question.setPrompt("Describe your day");

        when(reviewVideoAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(permissionEvaluationService.hasPermission(ACTOR_ID, "lms.review-video.manage")).thenReturn(true);
    }

    private ReflexQuestionProgressHistory attempt(int number, String score, String answer) {
        ReflexQuestionProgressHistory h = new ReflexQuestionProgressHistory();
        h.setStudent(student);
        h.setReviewVideoQuestion(question);
        h.setAttemptType(ReflexQuestionProgressHistory.AttemptType.WRITING);
        h.setAttemptNumber(number);
        h.setScore(new BigDecimal(score));
        h.setMaxScore(BigDecimal.valueOf(100));
        h.setFeedback("AI " + number);
        h.setAnswerText(answer);
        h.setMarkedAnswer("marked " + answer);
        h.setHint("hint " + number);
        h.setCriteriaScores(List.of(new CriteriaScoreItem("GRA", 70)));
        return h;
    }

    private Map<String, String> unzipCsvs(byte[] zip) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void exportReflexData_manifestHasStudentAnswerAndTeacherGradingPerAttempt() throws Exception {
        ReflexQuestionProgressHistory first = attempt(1, "80", "I go to school");
        ReflexQuestionProgressHistory second = attempt(2, "40", "I goes to school");
        first.setTeacherSelected(true);
        first.setTeacherScore(BigDecimal.valueOf(85));
        first.setTeacherFeedback("GV chốt");
        User teacher = new User();
        teacher.setFullName("Cô Lan");
        first.setTeacherBy(teacher);
        first.setTeacherAt(OffsetDateTime.parse("2026-10-20T10:00:00Z"));
        when(reflexQuestionProgressHistoryRepository.findByReviewVideoAssignmentIdOrderByStudentIdAscCreatedAtAsc(ASSIGNMENT_ID))
                .thenReturn(List.of(first, second));
        when(reflexQuestionProgressRepository.findByReviewVideoAssignmentId(ASSIGNMENT_ID)).thenReturn(List.of());

        String manifest = unzipCsvs(service.exportReflexData(ASSIGNMENT_ID, ACTOR_ID)).get("manifest.csv");

        String[] lines = manifest.replace("﻿", "").split("\n");
        assertThat(lines[0]).endsWith("bai_viet,bai_viet_danh_dau_loi,tieu_chi,cach_luyen,la_chinh_thuc,giao_vien_cham,giao_vien_cham_luc");
        // Lần 1 do giáo viên chọn nên là lần chính thức dù lần 2 mới hơn; lần 2 thì không.
        assertThat(lines[1]).contains("\"I go to school\"").contains("\"marked I go to school\"").contains("\"GRA: 70%\"")
                .contains("\"hint 1\"").contains(",1,85,\"GV chốt\",").endsWith(",1,\"Cô Lan\",2026-10-20T10:00Z");
        assertThat(lines[2]).contains("\"I goes to school\"").contains(",0,,,\"I goes to school\"").endsWith(",0,,");
    }

    @Test
    void exportReflexData_withoutSelection_latestAttemptIsOfficial() throws Exception {
        ReflexQuestionProgressHistory first = attempt(1, "80", "a");
        ReflexQuestionProgressHistory second = attempt(2, "40", "b");
        when(reflexQuestionProgressHistoryRepository.findByReviewVideoAssignmentIdOrderByStudentIdAscCreatedAtAsc(ASSIGNMENT_ID))
                .thenReturn(List.of(first, second));
        when(reflexQuestionProgressRepository.findByReviewVideoAssignmentId(ASSIGNMENT_ID)).thenReturn(List.of());

        String[] lines = unzipCsvs(service.exportReflexData(ASSIGNMENT_ID, ACTOR_ID)).get("manifest.csv").replace("﻿", "").split("\n");

        assertThat(lines[1]).contains(",0,,,\"a\"").contains(",\"hint 1\",0,,");
        assertThat(lines[2]).contains(",\"hint 2\",1,,");
    }

    @Test
    void exportReflexData_includesOfficialResultsFileWithAiOriginalAndReviewer() throws Exception {
        when(reflexQuestionProgressHistoryRepository.findByReviewVideoAssignmentIdOrderByStudentIdAscCreatedAtAsc(ASSIGNMENT_ID))
                .thenReturn(List.of());
        ReflexQuestionProgress progress = new ReflexQuestionProgress();
        progress.setStudent(student);
        progress.setReviewVideoQuestion(question);
        progress.setWritingAttemptCount(2);
        progress.setWritingScore(BigDecimal.valueOf(85));
        progress.setWritingMaxScore(BigDecimal.valueOf(100));
        progress.setWritingFeedback("GV chốt");
        progress.setWritingAiScore(BigDecimal.valueOf(40));
        progress.setWritingAiFeedback("AI gốc");
        User teacher = new User();
        teacher.setFullName("Cô Lan");
        progress.setWritingReviewedBy(teacher);
        progress.setWritingReviewedAt(OffsetDateTime.parse("2026-10-20T10:00:00Z"));
        when(reflexQuestionProgressRepository.findByReviewVideoAssignmentId(ASSIGNMENT_ID)).thenReturn(List.of(progress));

        String results = unzipCsvs(service.exportReflexData(ASSIGNMENT_ID, ACTOR_ID)).get("ket_qua_chinh_thuc.csv");

        String[] lines = results.replace("﻿", "").split("\n");
        assertThat(lines[0]).startsWith("ma_hoc_sinh,").endsWith("giao_vien_cham_lai,cham_lai_luc,nop_tre");
        // Bước Nói chưa nộp lần nào nên không có dòng.
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).contains("WRITING,2,85,100,\"GV chốt\",40,\"AI gốc\",\"Cô Lan\",2026-10-20T10:00Z,0");
    }
}
