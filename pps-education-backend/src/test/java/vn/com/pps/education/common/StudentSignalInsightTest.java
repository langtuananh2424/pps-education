package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-74 (bổ sung 2026-09-30) — điểm danh, chuyên cần, thông tin học sinh cho trợ lý nhận xét. */
class StudentSignalInsightTest {

    private static final LocalDate SESSION = LocalDate.of(2026, 9, 30);

    @Test
    void today_UC74_remindsOnlyFromTenMinutes() {
        assertThat(StudentSignalInsight.today("LATE", 10, null)).contains("hôm nay đến lớp muộn (nhắc nhẹ đến lớp đúng giờ)");
        assertThat(StudentSignalInsight.today("LATE", 9, null)).isEmpty();
        assertThat(StudentSignalInsight.today("EARLY_LEAVE", null, 15)).isPresent();
        assertThat(StudentSignalInsight.today("PRESENT", null, null)).isEmpty();
    }

    @Test
    void today_UC74_unknownMinutesIsNotReminded() {
        assertThat(StudentSignalInsight.today("LATE", null, null)).isEmpty();
        assertThat(StudentSignalInsight.today("EARLY_LEAVE", null, null)).isEmpty();
    }

    @Test
    void attendanceHistory_UC74_praisesEightPresentSessions() {
        assertThat(StudentSignalInsight.attendanceHistory(Collections.nCopies(8, "PRESENT")))
                .contains("đi học đầy đủ, đúng giờ nhiều buổi liên tiếp (khen sự chuyên cần)");
        // Chưa đủ 8 buổi dữ liệu thì chưa khen.
        assertThat(StudentSignalInsight.attendanceHistory(Collections.nCopies(7, "PRESENT"))).isEmpty();
    }

    @Test
    void attendanceHistory_UC74_remindsTwoUnexcusedAbsencesOrThreeLates() {
        assertThat(StudentSignalInsight.attendanceHistory(List.of("PRESENT", "ABSENT", "PRESENT", "ABSENT")))
                .contains("nghỉ học không phép nhiều buổi gần đây (nhắc nhẹ đi học đều, không trách móc)");
        assertThat(StudentSignalInsight.attendanceHistory(List.of("LATE", "PRESENT", "LATE", "LATE")))
                .contains("hay đến lớp muộn trong các buổi gần đây (nhắc nhẹ đến lớp đúng giờ)");
    }

    @Test
    void attendanceHistory_UC74_excusedAbsenceNotCountedAndOlderThanEightIgnored() {
        assertThat(StudentSignalInsight.attendanceHistory(List.of("EXCUSED", "EXCUSED", "PRESENT"))).isEmpty();
        // 2 lần vắng nằm ngoài 8 buổi gần nhất → không nhắc.
        List<String> statuses = new java.util.ArrayList<>(Collections.nCopies(8, "LATE").subList(0, 2));
        statuses.addAll(Collections.nCopies(6, "PRESENT"));
        statuses.addAll(List.of("ABSENT", "ABSENT"));
        assertThat(StudentSignalInsight.attendanceHistory(statuses)).isEmpty();
    }

    @Test
    void newStudent_UC74_withinThirtyDays() {
        assertThat(StudentSignalInsight.newStudent(SESSION.minusDays(30), SESSION)).isPresent();
        assertThat(StudentSignalInsight.newStudent(SESSION.minusDays(31), SESSION)).isEmpty();
        assertThat(StudentSignalInsight.newStudent(null, SESSION)).isEmpty();
    }

    @Test
    void ageTone_UC74_underTenOnly() {
        assertThat(StudentSignalInsight.ageTone(SESSION.minusYears(9), SESSION)).isPresent();
        assertThat(StudentSignalInsight.ageTone(SESSION.minusYears(10), SESSION)).isEmpty();
        assertThat(StudentSignalInsight.ageTone(null, SESSION)).isEmpty();
    }

    @Test
    void mentionsStudentInfo_UC74_detectsNewStudentAndAgePhrases() {
        assertThat(StudentSignalInsight.mentionsStudentInfo("Con mới vào lớp nhưng đã bắt nhịp nhanh.")).isTrue();
        assertThat(StudentSignalInsight.mentionsStudentInfo("Dù mới tham gia lớp, con rất tự tin.")).isTrue();
        assertThat(StudentSignalInsight.mentionsStudentInfo("Ở tuổi này con đã rất tự lập.")).isTrue();
        assertThat(StudentSignalInsight.mentionsStudentInfo("Con mới học xong bài và làm rất tốt.")).isFalse();
        assertThat(StudentSignalInsight.mentionsStudentInfo("Con tuổi trẻ tài cao")).isTrue();
    }
}
