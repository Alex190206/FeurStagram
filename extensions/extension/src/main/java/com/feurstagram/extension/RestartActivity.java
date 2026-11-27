package com.feurstagram.extension;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

/**
 * One-shot relay that makes "clear cache and restart" actually restart.
 *
 * The dying process cannot bring itself back: an activity it starts just before
 * killing itself is dropped by the system ("app died, no saved state"), and one
 * queued for after the kill (an alarm PendingIntent) is refused as a background
 * activity launch. So the relay runs in a process of its own — declared by the
 * Restart relay patch — kills the main process from the outside, and only then
 * starts Instagram from its own live foreground window.
 */
public final class RestartActivity extends Activity {

    /** Pid of the process to kill, put in by {@link CacheCleaner}. */
    public static final String EXTRA_PID = "com.feurstagram.extension.RESTART_PID";

    private static final String EXTRA_UNLOCK_HOME = "com.feurstagram.extension.UNLOCK_HOME";

    /** Intent that launches this non-exported relay in its own task. */
    static Intent intentFor(Context context, int pid, boolean unlock) {
        Intent intent = new Intent(context, RestartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(EXTRA_PID, pid);
        intent.putExtra(EXTRA_UNLOCK_HOME, unlock);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) restart(getIntent());
        finish();
    }

    private void restart(Intent request) {
        boolean unlock = request.getBooleanExtra(EXTRA_UNLOCK_HOME, false);
        try {
            int pid = request.getIntExtra(EXTRA_PID, -1);
            Intent target = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (target == null || pid <= 0 || pid == Process.myPid()) return;

            if (unlock && !FeedUnlockSession.issue(getNoBackupFilesDir(), pid,
                    System.currentTimeMillis(), SystemClock.elapsedRealtime())) return;

            Process.killProcess(pid);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(target);
        } catch (RuntimeException failure) {
            if (unlock) FeedUnlockSession.cancel(getNoBackupFilesDir());
        }
    }
}
