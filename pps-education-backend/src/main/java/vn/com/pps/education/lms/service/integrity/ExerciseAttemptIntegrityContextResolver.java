package vn.com.pps.education.lms.service.integrity;

import org.springframework.stereotype.Component;
import vn.com.pps.education.lms.domain.AttemptIntegrityEvent.AttemptType;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.student.repository.StudentRepository;

@Component
public class ExerciseAttemptIntegrityContextResolver implements AttemptIntegrityContextResolver {

    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final StudentRepository studentRepository;

    public ExerciseAttemptIntegrityContextResolver(ExerciseAttemptRepository exerciseAttemptRepository,
                                                     StudentRepository studentRepository) {
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public boolean supports(AttemptType type) {
        return type == AttemptType.EXERCISE;
    }

    @Override
    public AttemptContext resolveForOwner(Long attemptId, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttemptIntegrityContext.studentProfileNotFound",
                        new Object[]{actorUserId}, "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttemptIntegrityContext.attemptNotFound",
                        new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId));
        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("error.exerciseAttemptIntegrityContext.attemptNotFound",
                    new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId);
        }
        return toContext(attempt);
    }

    @Override
    public AttemptContext resolveForReading(Long attemptId) {
        ExerciseAttempt attempt = exerciseAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exerciseAttemptIntegrityContext.attemptNotFound",
                        new Object[]{attemptId}, "Không tìm thấy lượt làm bài id=" + attemptId));
        return toContext(attempt);
    }

    private AttemptContext toContext(ExerciseAttempt attempt) {
        var schoolClass = attempt.getExerciseAssignment() == null ? null : attempt.getExerciseAssignment().getSchoolClass();
        return new AttemptContext(attempt.getStudent(), schoolClass, "đề \"" + attempt.getExercise().getTitle() + "\"");
    }
}
