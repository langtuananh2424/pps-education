package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.lms.domain.ExerciseSection;
import vn.com.pps.education.student.domain.Student;

import java.math.BigDecimal;

/** Mức riêng của bài tập con, nếu NULL thì mức của Bài ({@code exercises.pass_threshold_percent}). */
@Component
public class DefaultPassThresholdResolver implements PassThresholdResolver {

    @Override
    public BigDecimal thresholdFor(ExerciseSection section, Student student) {
        return section.getPassThresholdPercent() != null
                ? section.getPassThresholdPercent()
                : section.getExercise().getPassThresholdPercent();
    }
}
