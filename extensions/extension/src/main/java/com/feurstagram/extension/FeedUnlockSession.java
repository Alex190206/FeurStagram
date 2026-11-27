package com.feurstagram.extension;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Private, short-lived handoff from the restart activity to one main process. */
final class FeedUnlockSession {
    private static final String TICKET = "feurstagram-home-restart-ticket";
    private static final long HANDOFF_MS = 30_000;
    private static final int VERSION = 1;

    private FeedUnlockSession() {}

    static boolean issue(File directory, int previousPid, long wallTime, long uptime) {
        File ticket = new File(directory, TICKET);
        File pending = new File(directory, TICKET + ".pending");
        if (ticket.exists() && !ticket.delete()) return false;
        try {
            try (FileOutputStream stream = new FileOutputStream(pending);
                 DataOutputStream output = new DataOutputStream(stream)) {
                output.writeInt(VERSION);
                output.writeInt(previousPid);
                output.writeLong(wallTime);
                output.writeLong(uptime);
                output.flush();
                stream.getFD().sync();
            }
            return pending.renameTo(ticket);
        } catch (IOException | SecurityException ignored) {
            return false;
        } finally {
            pending.delete();
        }
    }

    static boolean consume(File directory, int pid, long wallTime, long uptime) {
        File ticket = new File(directory, TICKET);
        try (DataInputStream input = new DataInputStream(new FileInputStream(ticket))) {
            if (input.readInt() != VERSION) return false;
            if (input.readInt() == pid) return false;
            long wallAge = wallTime - input.readLong();
            long elapsedAge = uptime - input.readLong();
            return input.read() == -1 && wallAge >= 0 && wallAge <= HANDOFF_MS
                    && elapsedAge >= 0 && elapsedAge <= HANDOFF_MS && ticket.delete();
        } catch (IOException | SecurityException ignored) {
            return false;
        } finally {
            // Expired or malformed handoffs must not survive this launch.
            ticket.delete();
        }
    }

    static void cancel(File directory) {
        new File(directory, TICKET).delete();
    }
}
