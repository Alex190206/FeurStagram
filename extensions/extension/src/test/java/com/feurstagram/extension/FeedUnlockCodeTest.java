package com.feurstagram.extension;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class FeedUnlockCodeTest {
    /**
     * Set FEURSTAGRAM_TEST_UNLOCK_CODE to verify the configured owner code.
     * Keep the plaintext out of this public repository and CI logs.
     */
    @Test public void ownerCodeUnlocksWhenSuppliedLocally() {
        String code = System.getenv("FEURSTAGRAM_TEST_UNLOCK_CODE");
        assumeTrue(code != null && !code.isEmpty());
        assertTrue(FeedUnlockCode.matches(code));
        assertFalse(FeedUnlockCode.matches(code + " "));
        assertFalse(FeedUnlockCode.matches(" " + code));
        assertFalse(FeedUnlockCode.matches(code.substring(1)));
        assertFalse(FeedUnlockCode.matches(code + "0"));
    }

    @Test public void wrongOrEmptyCodesStayLocked() {
        assertFalse(FeedUnlockCode.matches(null));
        assertFalse(FeedUnlockCode.matches(""));
        assertFalse(FeedUnlockCode.matches("0"));
        assertFalse(FeedUnlockCode.matches("1234"));
        assertFalse(FeedUnlockCode.matches("not the password"));
        assertFalse(FeedUnlockCode.matches("00000000000000000"));
    }
}
