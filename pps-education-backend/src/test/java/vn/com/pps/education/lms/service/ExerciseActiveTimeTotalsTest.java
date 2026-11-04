package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.student.domain.Student;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ExerciseReportService#timeTotalsByStudent — cột "Thời gian làm" cộng mọi lượt, kể cả làm lại. */
class ExerciseActiveTimeTotalsTest {

    private static ExerciseAttempt attempt(long studentId, int day, int activeSeconds) {
        Student student = new Student();
        student.setId(studentId);
        ExerciseAttempt a = new ExerciseAttempt();
        a.setStudent(student);
        a.setStartedAt(OffsetDateTime.of(2026, 11, day, 8, 0, 0, 0, ZoneOffset.UTC));
        a.setActiveSeconds(activeSeconds);
        return a;
    }

    @Test
    void timeTotalsByStudent_UC66_MainFlow_sumsAllAttemptsIncludingRetakes() {
        Map<Long, ExerciseReportService.TimeTotals> totals = ExerciseReportService.timeTotalsByStudent(List.of(
                attempt(1, 3, 90), attempt(1, 1, 0), attempt(1, 2, 300), attempt(2, 2, 45)));

        assertThat(totals.get(1L).activeSeconds()).isEqualTo(390);
        // Lượt đầu tiên (ngày 1, làm trước khi có V223 nên 0 giây) vẫn là mốc bắt đầu.
        assertThat(totals.get(1L).firstStartedAt().getDayOfMonth()).isEqualTo(1);
        assertThat(totals.get(2L).activeSeconds()).isEqualTo(45);
        assertThat(totals).doesNotContainKey(3L);
    }
}
