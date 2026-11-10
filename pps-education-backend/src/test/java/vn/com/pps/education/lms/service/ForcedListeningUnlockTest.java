package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V226 (Nghe ép) — công thức mở tapescript theo số lượt chưa đạt: Alternate Flow A3 trong docs/uc/phan-he-07-lms-portal.md. */
class ForcedListeningUnlockTest {

    @Test
    void defaultThreeAndTwo_unlocksAtAttempts3_5_7() {
        assertEquals(0, ForcedListeningService.unlockedCount(0, 3, 2));
        assertEquals(0, ForcedListeningService.unlockedCount(2, 3, 2));
        assertEquals(1, ForcedListeningService.unlockedCount(3, 3, 2));
        assertEquals(1, ForcedListeningService.unlockedCount(4, 3, 2));
        assertEquals(2, ForcedListeningService.unlockedCount(5, 3, 2));
        assertEquals(3, ForcedListeningService.unlockedCount(7, 3, 2));
    }

    @Test
    void customTwoAndOne_unlocksEveryAttemptFromSecond() {
        assertEquals(0, ForcedListeningService.unlockedCount(1, 2, 1));
        assertEquals(1, ForcedListeningService.unlockedCount(2, 2, 1));
        assertEquals(2, ForcedListeningService.unlockedCount(3, 2, 1));
    }

    @Test
    void attemptsUntilNextUnlock_countsDownToNextMilestone() {
        assertEquals(3, ForcedListeningService.attemptsUntilNextUnlock(0, 3, 2));
        assertEquals(1, ForcedListeningService.attemptsUntilNextUnlock(2, 3, 2));
        assertEquals(2, ForcedListeningService.attemptsUntilNextUnlock(3, 3, 2));
        assertEquals(1, ForcedListeningService.attemptsUntilNextUnlock(4, 3, 2));
        assertEquals(2, ForcedListeningService.attemptsUntilNextUnlock(5, 3, 2));
    }
}
