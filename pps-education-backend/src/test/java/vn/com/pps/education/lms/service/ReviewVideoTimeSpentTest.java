package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ReviewVideoReportService#sumSittingSeconds — tổng thời gian làm bài theo phiên. */
class ReviewVideoTimeSpentTest {

    private static OffsetDateTime at(int minute, int second) {
        return OffsetDateTime.of(2026, 11, 4, 8, minute, second, 0, ZoneOffset.UTC);
    }

    @Test
    void sumSittingSeconds_UC23b_MainFlow_sumsEachSittingUntilItsLastSubmission() {
        // Phiên 1: 08:00 → nộp cuối 08:05 (5 phút). Phiên 2 (08:30) bỏ dở. Phiên 3: 08:40 → 08:42:30.
        Integer seconds = ReviewVideoReportService.sumSittingSeconds(
                List.of(at(40, 0), at(0, 0), at(30, 0)),
                List.of(at(2, 0), at(5, 0), at(42, 30)));
        assertThat(seconds).isEqualTo(5 * 60 + 150);
    }

    @Test
    void sumSittingSeconds_UC23b_noData_returnsNull() {
        assertThat(ReviewVideoReportService.sumSittingSeconds(null, List.of(at(1, 0)))).isNull();
        assertThat(ReviewVideoReportService.sumSittingSeconds(List.of(at(0, 0)), null)).isNull();
        // Lần nộp trước mọi phiên (dữ liệu cũ) không được tính.
        assertThat(ReviewVideoReportService.sumSittingSeconds(List.of(at(10, 0)), List.of(at(5, 0)))).isNull();
    }
}
