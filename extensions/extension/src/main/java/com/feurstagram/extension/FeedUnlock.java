package com.feurstagram.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

/** One masked password prompt shared by Settings and the caught-up card. */
public final class FeedUnlock {
    private FeedUnlock() {}

    static boolean prompt(Context context) {
        if (!(context instanceof Activity)) return false;
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return false;
        try {
            EditText entry = new EditText(activity);
            entry.setSingleLine(true);
            entry.setHint("Password");
            entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
            entry.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            entry.setSaveEnabled(false);
            if (Build.VERSION.SDK_INT >= 26) entry.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

            FrameLayout frame = new FrameLayout(activity);
            int inset = (int) (24 * activity.getResources().getDisplayMetrics().density + 0.5f);
            frame.setPadding(inset, 0, inset, 0);
            frame.addView(entry, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Unlock more Home posts")
                    .setMessage("Enter the password to show recommendations for this app launch. Instagram will reopen.")
                    .setView(frame)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Unlock", null)
                    .create();
            Runnable submit = () -> {
                Config.UnlockResult result = Config.requestCaughtUpUnlock(activity, entry.getText().toString());
                entry.setText("");
                if (result == Config.UnlockResult.RESTARTING) {
                    dialog.dismiss();
                } else if (result == Config.UnlockResult.INCORRECT_PASSWORD) {
                    entry.setError("Incorrect password");
                } else {
                    entry.setError("Couldn't reopen Instagram. Try again.");
                }
            };
            entry.setOnEditorActionListener((view, action, event) -> {
                if (action != EditorInfo.IME_ACTION_DONE) return false;
                submit.run();
                return true;
            });
            dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(button -> submit.run()));
            dialog.setOnDismissListener(ignored -> entry.setText(""));
            entry.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            dialog.show();
            return true;
        } catch (RuntimeException failure) {
            Toast.makeText(activity, "Unable to open unlock prompt", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
