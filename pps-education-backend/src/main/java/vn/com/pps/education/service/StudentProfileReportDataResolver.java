package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.ReportTemplate;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-68: resolver cho {@link ReportTemplate.TemplateType#STUDENT_PROFILE}
 * (Hồ sơ học sinh) — nguồn dữ liệu: students, users, parents,
 * parent_student (xem "Dữ liệu nguồn theo loại báo cáo" trong UC-67,
 * docs/uc/phan-he-06-hoc-thuat.md). Không có tính toán/công thức mơ hồ
 * (khác TRANSCRIPT/GRADE_REPORT còn cần làm rõ cách chọn kỳ đánh giá) nên
 * triển khai trước làm ví dụ tham chiếu cho các resolver sau.
 */
@Component
public class StudentProfileReportDataResolver implements ReportDataResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;

    public StudentProfileReportDataResolver(StudentRepository studentRepository,
                                              ParentStudentRepository parentStudentRepository) {
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
    }

    @Override
    public ReportTemplate.TemplateType supports() {
        return ReportTemplate.TemplateType.STUDENT_PROFILE;
    }

    /**
     * Key công bố: STUDENT_NAME, STUDENT_CODE, DATE_OF_BIRTH, GENDER,
     * PRIMARY_SITE, STATUS, ENROLLMENT_DATE, PRIMARY_PARENT_NAME,
     * PRIMARY_PARENT_PHONE, PRIMARY_PARENT_EMAIL, FINANCIAL_GUARDIAN_NAME,
     * GENERATED_DATE.
     */
    @Override
    public Map<String, Object> buildContext(ReportGenerationParams params) {
        Student student = studentRepository.findById(params.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("error.studentProfileReportData.studentNotFoundById", new Object[]{params.studentId()}, "Không tìm thấy học sinh id=" + params.studentId()));

        Map<String, Object> context = new HashMap<>();
        context.put("STUDENT_NAME", student.getUser().getFullName());
        context.put("STUDENT_CODE", student.getStudentCode());
        context.put("DATE_OF_BIRTH", student.getDateOfBirth().format(DATE_FORMAT));
        if (student.getGender() != null) {
            context.put("GENDER", student.getGender().name());
        }
        if (student.getPrimarySite() != null) {
            context.put("PRIMARY_SITE", student.getPrimarySite().getName());
        }
        context.put("STATUS", student.getStatus().name());
        context.put("ENROLLMENT_DATE", student.getEnrollmentDate().format(DATE_FORMAT));

        List<ParentStudent> links = parentStudentRepository.findByStudentId(student.getId());
        links.stream().filter(ParentStudent::isPrimaryContact).findFirst().ifPresent(link -> {
            Parent parent = link.getParent();
            context.put("PRIMARY_PARENT_NAME", parent.getUser().getFullName());
            context.put("PRIMARY_PARENT_PHONE", parent.getUser().getPhone());
            context.put("PRIMARY_PARENT_EMAIL", parent.getUser().getEmail());
        });
        links.stream().filter(ParentStudent::isFinancialResponsible).findFirst().ifPresent(link ->
                context.put("FINANCIAL_GUARDIAN_NAME", link.getParent().getUser().getFullName()));

        context.put("GENERATED_DATE", LocalDate.now().format(DATE_FORMAT));
        return context;
    }
}
