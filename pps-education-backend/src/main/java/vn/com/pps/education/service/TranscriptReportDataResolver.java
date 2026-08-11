package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.GradeComponentSetup;
import vn.com.pps.education.domain.GradeEntry;
import vn.com.pps.education.domain.GradeEvaluationResult;
import vn.com.pps.education.domain.ReportTemplate;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.dto.ReportPeriodSelector;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradeEvaluationResultRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-68: resolver cho {@link ReportTemplate.TemplateType#TRANSCRIPT}
 * (Phiếu kết quả lộ trình) — nguồn dữ liệu: students, classes,
 * grade_entries, grade_evaluation_results, class_teachers (xem "Dữ liệu
 * nguồn theo loại báo cáo" trong UC-67, docs/uc/phan-he-06-hoc-thuat.md).
 *
 * Xuất linh hoạt theo giữa kỳ/cuối kỳ/cả kỳ/cả năm học — đã xác nhận với
 * người dùng 2026-08-09: KHÔNG có quy ước cố định "S1/S2" — actor tự chọn
 * 1 hoặc nhiều {@link ReportPeriodSelector} (label tự đặt, VD "S1", "MID1")
 * khi gọi UC-68, phải khớp đúng hậu tố người tạo mẫu đã dùng ở UC-67 bước 3
 * (VD placeholder {@code [READING_S1]} cần label "S1"). Ví dụ:
 * <ul>
 *   <li>Chỉ giữa kỳ 1: 1 selector (label="MID1", evaluationType=MID_TERM).</li>
 *   <li>Chỉ cuối kỳ 1: 1 selector (label="END1", evaluationType=END_TERM).</li>
 *   <li>Cả kỳ 1: 2 selector cùng academicTermId (MID_TERM + END_TERM).</li>
 *   <li>Cả năm học: nhiều selector, mỗi học kỳ 1-2 selector.</li>
 * </ul>
 * Mỗi selector publish context key {@code {SKILL_CODE}_{label}} (từ
 * grade_entries.gradeComponent.code, VD READING_S1) cho từng thành phần
 * điểm, và {@code OVERALL_{label}}/{@code LEVEL_{label}}/
 * {@code COMMENT_{label}} (từ grade_evaluation_results — Overall/Level do
 * Giáo viên tự nhập/import, hệ thống KHÔNG tính lại, xem Javadoc
 * {@link GradeEvaluationResult}).
 */
@Component
public class TranscriptReportDataResolver implements ReportDataResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final GradeEvaluationResultRepository gradeEvaluationResultRepository;
    private final ClassTeacherRepository classTeacherRepository;

    public TranscriptReportDataResolver(StudentRepository studentRepository,
                                          SchoolClassRepository schoolClassRepository,
                                          GradeEntryRepository gradeEntryRepository,
                                          GradeEvaluationResultRepository gradeEvaluationResultRepository,
                                          ClassTeacherRepository classTeacherRepository) {
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.gradeEvaluationResultRepository = gradeEvaluationResultRepository;
        this.classTeacherRepository = classTeacherRepository;
    }

    @Override
    public ReportTemplate.TemplateType supports() {
        return ReportTemplate.TemplateType.TRANSCRIPT;
    }

    @Override
    public Map<String, Object> buildContext(ReportGenerationParams params) {
        if (params.classId() == null) {
            throw new IllegalArgumentException("TRANSCRIPT cần classId (điểm số gắn theo lớp).");
        }
        Student student = studentRepository.findById(params.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + params.studentId()));
        SchoolClass schoolClass = schoolClassRepository.findById(params.classId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp id=" + params.classId()));

        Map<String, Object> context = new HashMap<>();
        context.put("STUDENT_NAME", student.getUser().getFullName());
        context.put("STUDENT_CODE", student.getStudentCode());
        context.put("CLASS_NAME", schoolClass.getName());
        context.put("SCHOOL_NAME", schoolClass.getSite().getName());
        if (schoolClass.getAcademicYear() != null) {
            context.put("ACADEMIC_YEAR", schoolClass.getAcademicYear().getName());
        }
        classTeacherRepository.findBySchoolClassIdAndAssignedToIsNull(schoolClass.getId()).stream()
                .filter(ct -> ct.getTeacherRole() == ClassTeacher.TeacherRole.PRIMARY)
                .findFirst()
                .ifPresent(ct -> context.put("PRIMARY_TEACHER_NAME", ct.getTeacher().getFullName()));
        context.put("GENERATED_DATE", LocalDate.now().format(DATE_FORMAT));

        List<GradeEntry> allEntries = gradeEntryRepository.findBySchoolClassIdAndStudentId(schoolClass.getId(), student.getId());

        for (ReportPeriodSelector period : params.periods()) {
            GradeComponentSetup.EvaluationType evaluationType = parseEvaluationType(period.evaluationType());

            allEntries.stream()
                    .filter(e -> e.getAcademicTerm().getId().equals(period.academicTermId()) && e.getEvaluationType() == evaluationType)
                    .forEach(e -> context.put(e.getGradeComponent().getCode().name() + "_" + period.label(), e.getScore()));

            gradeEvaluationResultRepository.findBySchoolClassIdAndStudentIdAndAcademicTermIdAndEvaluationType(
                    schoolClass.getId(), student.getId(), period.academicTermId(), evaluationType
            ).ifPresent(result -> {
                if (result.getOverallScore() != null) {
                    context.put("OVERALL_" + period.label(), result.getOverallScore());
                }
                if (result.getLevel() != null) {
                    context.put("LEVEL_" + period.label(), result.getLevel());
                }
                if (result.getComment() != null) {
                    context.put("COMMENT_" + period.label(), result.getComment());
                }
            });
        }
        return context;
    }

    private GradeComponentSetup.EvaluationType parseEvaluationType(String value) {
        try {
            return GradeComponentSetup.EvaluationType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("evaluationType không hợp lệ: " + value
                    + ". Giá trị hợp lệ: " + java.util.Arrays.toString(GradeComponentSetup.EvaluationType.values()));
        }
    }
}
