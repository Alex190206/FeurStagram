package com.feurstagram.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Prompts before relaxing the caught-up cutoff. Re-locking does not require a code.
 * Settings and the caught-up card share this one verification path.
 */
public final class FeedUnlock {
    private FeedUnlock() {}

    public static void prompt(Context context, Runnable afterUnlock) {
        if (!(context instanceof Activity)) return;
        try {
            EditText entry = new EditText(context);
            entry.setSingleLine(true);
            entry.setHint("Password");
            entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});

            FrameLayout frame = new FrameLayout(context);
            int inset = (int) (24 * context.getResources().getDisplayMetrics().density + 0.5f);
            frame.setPadding(inset, 0, inset, 0);
            frame.addView(entry, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Unlock more Home posts")
                    .setMessage("Enter the password to show recommendations after you're all caught up.")
                    .setView(frame)
                    .setNegativeButton("Cancel", (d, which) -> {})
                    .setPositiveButton("Unlock", null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(button -> {
                        String code = entry.getText().toString();
                        if (!FeedUnlockCode.matches(code)) {
                            entry.setText("");
                            entry.setError("Incorrect password");
                            return;
                        }
                        entry.setText("");
                        Config.unlockCaughtUpForNextLaunch();
                        Config.setNeedsRestart();
                        dialog.dismiss();
                        if (afterUnlock != null) afterUnlock.run();
                    }));
            dialog.setOnDismissListener(ignored -> entry.setText(""));
            dialog.show();
        } catch (Throwable failure) {
            Toast.makeText(context, "Unable to open unlock prompt", Toast.LENGTH_SHORT).show();
        }
    }
}
