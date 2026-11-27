package com.feurstagram.extension;

import org.junit.Test;
import static org.junit.Assert.*;

public class FeedUnlockCodeTest {
    @Test public void correctCodeUnlocks() {
        assertTrue(FeedUnlockCode.matches("12345678987654321"));
    }

    @Test public void wrongOrIncompleteCodesStayLocked() {
        assertFalse(FeedUnlockCode.matches(null));
        assertFalse(FeedUnlockCode.matches(""));
        assertFalse(FeedUnlockCode.matches("1234567898765432"));
        assertFalse(FeedUnlockCode.matches("123456789876543210"));
        assertFalse(FeedUnlockCode.matches("12345678987654322"));
        assertFalse(FeedUnlockCode.matches(" 12345678987654321"));
        assertFalse(FeedUnlockCode.matches("12345678987654321 "));
        assertFalse(FeedUnlockCode.matches("012345678987654321"));
    }
}
