package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khung đêm của nhắc hạn BTVN (V195, đã xác nhận với người dùng 2026-09-25) — kiểm tra thuần
 * {@link HomeworkDueSoonReminderSchedulerService#reminderSendAt} với cấu hình mặc định: nhắc trước
 * 12 tiếng, khung đêm 21:00–07:00 giờ VN. Không chạm DB.
 */
class HomeworkDueSoonReminderSendAtTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final int BEFORE_DUE_HOURS = 12;
    private static final int QUIET_START = 21;
    private static final int QUIET_END = 7;

    @Test
    void reminderSendAt_MainFlow_keepsTwelveHoursBeforeWhenOutsideQuietHours() {
        // Hạn 08:00 -> mốc 20:00 hôm trước, chưa tới khung đêm
        assertThat(sendAt(vn(26, 8, 0))).isEqualTo(vn(25, 20, 0));
        // Hạn 21:00 -> mốc 09:00 cùng ngày
        assertThat(sendAt(vn(26, 21, 0))).isEqualTo(vn(26, 9, 0));
    }

    @Test
    void reminderSendAt_A1_movesToNineLastEveningWhenSlotAfterMidnight() {
        // Hạn 12:00 trưa -> mốc 0:00 -> kéo về 21:00 tối hôm trước
        assertThat(sendAt(vn(26, 12, 0))).isEqualTo(vn(25, 21, 0));
        // Hạn 17:00 -> mốc 05:00 sáng -> 21:00 tối hôm trước
        assertThat(sendAt(vn(26, 17, 0))).isEqualTo(vn(25, 21, 0));
        // Hạn 18:59 -> mốc 06:59 vẫn trong khung đêm
        assertThat(sendAt(vn(26, 18, 59))).isEqualTo(vn(25, 21, 0));
    }

    @Test
    void reminderSendAt_A2_movesToNineSameEveningWhenSlotBeforeMidnight() {
        // Hạn 10:00 -> mốc 22:00 hôm trước -> 21:00 cùng tối đó
        assertThat(sendAt(vn(26, 10, 0))).isEqualTo(vn(25, 21, 0));
        // Hạn đúng 09:00 -> mốc đúng 21:00 (biên đầu khung đêm) -> giữ 21:00
        assertThat(sendAt(vn(26, 9, 0))).isEqualTo(vn(25, 21, 0));
    }

    @Test
    void reminderSendAt_A3_quietEndIsExclusive() {
        // Hạn 19:00 -> mốc đúng 07:00 = hết khung đêm -> gửi 07:00 như bình thường
        assertThat(sendAt(vn(26, 19, 0))).isEqualTo(vn(26, 7, 0));
    }

    @Test
    void reminderSendAt_A4_assignedLateAtNightIsAlreadyDueSoSentImmediately() {
        // Giao 22:30 hạn 10:00 sáng hôm sau: mốc 21:00 đã qua so với lúc giao -> lượt quét kế tiếp gửi ngay
        OffsetDateTime assignedAt = vn(25, 22, 30);
        assertThat(sendAt(vn(26, 10, 0))).isBeforeOrEqualTo(assignedAt);
        // Giao 22:30 hạn 08:00 sáng: mốc 20:00 cũng đã qua -> gửi ngay
        assertThat(sendAt(vn(26, 8, 0))).isBeforeOrEqualTo(assignedAt);
    }

    @Test
    void reminderSendAt_A5_equalStartEndDisablesQuietHours() {
        assertThat(HomeworkDueSoonReminderSchedulerService.reminderSendAt(vn(26, 12, 0), BEFORE_DUE_HOURS, 21, 21))
                .isEqualTo(vn(26, 0, 0));
    }

    @Test
    void reminderSendAt_A6_dueAtStoredInUtcStillUsesVietnamClock() {
        // 05:00Z = 12:00 giờ VN -> 21:00 VN tối hôm trước (= 14:00Z)
        OffsetDateTime dueUtc = OffsetDateTime.of(2026, 9, 26, 5, 0, 0, 0, ZoneOffset.UTC);
        assertThat(sendAt(dueUtc).isEqual(vn(25, 21, 0))).isTrue();
    }

    private static OffsetDateTime sendAt(OffsetDateTime dueAt) {
        return HomeworkDueSoonReminderSchedulerService.reminderSendAt(dueAt, BEFORE_DUE_HOURS, QUIET_START, QUIET_END);
    }

    private static OffsetDateTime vn(int day, int hour, int minute) {
        return OffsetDateTime.of(2026, 9, day, hour, minute, 0, 0, VN);
    }
}
