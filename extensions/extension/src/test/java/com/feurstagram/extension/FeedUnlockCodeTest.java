package com.feurstagram.extension;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class FeedUnlockCodeTest {
    /**
     * Supply -Dfeurstagram.unlock.code=... to verify the configured owner code.
     * Keep the plaintext out of this public repository and CI logs.
     */
    @Test public void ownerCodeUnlocksWhenSuppliedLocally() {
        String code = System.getProperty("feurstagram.unlock.code");
        assumeTrue(code != null && !code.isEmpty());
        assertTrue(FeedUnlockCode.matches(code));
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
