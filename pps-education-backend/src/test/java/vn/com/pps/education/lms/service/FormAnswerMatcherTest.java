package vn.com.pps.education.lms.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Nghe điền phiếu thông tin — nhiều đáp án mỗi ô ("/") và chuẩn hóa số/giờ. */
class FormAnswerMatcherTest {

    @Test
    void timeFormatsAreEquivalent() {
        for (String given : new String[]{"7:30", "7.30", "7h30", " 7:30 "}) {
            assertTrue(FormAnswerMatcher.matches("7:30", given), given);
        }
        assertTrue(FormAnswerMatcher.matches("7.30 p.m.", "7:30pm"));
        assertTrue(FormAnswerMatcher.matches("7:30 pm", "7.30 P.M."));
        assertFalse(FormAnswerMatcher.matches("7:30", "7:45"));
    }

    @Test
    void numberWordsAndDigitsAreEquivalent() {
        assertTrue(FormAnswerMatcher.matches("five", "5"));
        assertTrue(FormAnswerMatcher.matches("5", "Five"));
        assertTrue(FormAnswerMatcher.matches("2,500", "2500"));
        assertFalse(FormAnswerMatcher.matches("five", "6"));
    }

    @Test
    void alternativesAndCaseAndTrailingPunctuation() {
        assertTrue(FormAnswerMatcher.matches("1999/nineteen ninety-nine", "Nineteen Ninety-Nine."));
        assertTrue(FormAnswerMatcher.matches("popcorn", "POPCORN"));
        assertFalse(FormAnswerMatcher.matches("popcorn", "ice cream"));
    }

    @Test
    void blankExpectedMatchesOnlyBlankGiven() {
        assertTrue(FormAnswerMatcher.matches("", null));
        assertTrue(FormAnswerMatcher.matches("", "  "));
        assertFalse(FormAnswerMatcher.matches("", "the"));
        assertFalse(FormAnswerMatcher.matches("the", null));
    }
}
