package com.feurstagram.extension;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public final class FeedUnlockSessionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void freshLaunchIsLocked() {
        assertFalse(FeedUnlockSession.consume(temporary.getRoot(), 2, 100, 100));
    }

    @Test public void correctHandoffUnlocksOnlyOneNewProcess() {
        File directory = temporary.getRoot();
        assertTrue(FeedUnlockSession.issue(directory, 1, 100, 100));
        assertTrue(FeedUnlockSession.consume(directory, 2, 200, 200));
        assertFalse(FeedUnlockSession.consume(directory, 3, 201, 201));
    }

    @Test public void originalProcessCannotActivateItsOwnHandoff() {
        File directory = temporary.getRoot();
        assertTrue(FeedUnlockSession.issue(directory, 1, 100, 100));
        assertFalse(FeedUnlockSession.consume(directory, 1, 101, 101));
    }

    @Test public void cancellationRelocksWithoutPassword() {
        File directory = temporary.getRoot();
        assertTrue(FeedUnlockSession.issue(directory, 1, 100, 100));
        FeedUnlockSession.cancel(directory);
        assertFalse(FeedUnlockSession.consume(directory, 2, 200, 200));
    }

    @Test public void staleOrRebootedHandoffStaysLocked() {
        File directory = temporary.getRoot();
        assertTrue(FeedUnlockSession.issue(directory, 1, 100, 100));
        assertFalse(FeedUnlockSession.consume(directory, 2, 30_101, 30_101));
        assertTrue(FeedUnlockSession.issue(directory, 1, 100, 100));
        assertFalse(FeedUnlockSession.consume(directory, 2, 101, 10));
    }

    @Test public void malformedHandoffStaysLocked() throws Exception {
        File directory = temporary.getRoot();
        assertTrue(FeedUnlockSession.issue(directory, 1, 100, 100));
        File[] files = directory.listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        try (FileOutputStream output = new FileOutputStream(files[0])) {
            output.write(1);
        }
        assertFalse(FeedUnlockSession.consume(directory, 2, 200, 200));
        assertFalse(files[0].exists());
    }
}
