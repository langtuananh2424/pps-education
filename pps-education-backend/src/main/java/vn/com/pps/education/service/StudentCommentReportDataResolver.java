package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.ReportTemplate;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.StudentCommentRepository;
import vn.com.pps.education.repository.StudentRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class StudentCommentReportDataResolver implements ReportDataResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentRepository studentRepository;
    private final StudentCommentRepository studentCommentRepository;

    public StudentCommentReportDataResolver(StudentRepository studentRepository,
                                             StudentCommentRepository studentCommentRepository) {
        this.studentRepository = studentRepository;
        this.studentCommentRepository = studentCommentRepository;
    }

    @Override
    public ReportTemplate.TemplateType supports() {
        return ReportTemplate.TemplateType.STUDENT_COMMENT;
    }

    @Override
    public Map<String, Object> buildContext(ReportGenerationParams params) {
        Map<String, Object> context = new HashMap<>();
        context.put("GENERATED_DATE", LocalDate.now().format(DATE_FORMAT));

        if (params.studentId() != null) {
            Student student = studentRepository.findById(params.studentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + params.studentId()));
            context.put("STUDENT_NAME", student.getUser().getFullName());
            context.put("STUDENT_CODE", student.getStudentCode());

            if (params.classSessionId() != null) {
                studentCommentRepository.findByClassSessionId(params.classSessionId()).stream()
                        .filter(c -> c.getStudent().getId().equals(student.getId()))
                        .findFirst()
                        .ifPresent(c -> context.put("STUDENT_COMMENT", c.getContent()));
            }
        }

        return context;
    }
}
