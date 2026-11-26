package dev.esuite.uitests;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;

/** Deterministic software keyboard for disposable emulators. */
public final class FixtureIme extends InputMethodService {
    private static final String TAG = "ESuiteFixtureIme";

    private Button typeKey;
    private Button pasteKey;

    @Override public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (typeKey != null) typeKey.setContentDescription(null);
        if (pasteKey != null) pasteKey.setContentDescription(null);
    }

    @Override public View onCreateInputView() {
        Button key = new Button(this);
        typeKey = key;
        key.setText("Type fixture text");
        key.setAllCaps(false);
        key.setOnClickListener(view -> {
            InputConnection connection = getCurrentInputConnection();
            boolean composing = connection != null && connection.setComposingText("Es", 1);
            boolean committed = connection != null && connection.commitText("Esuite42", 1);
            key.setContentDescription(committed ? "Input accepted" : "Input rejected");
            Log.i(TAG, "Type key: input connection=" + (connection != null) + ", composing=" + composing + ", commit=" + committed);
        });
        Button paste = new Button(this);
        pasteKey = paste;
        paste.setText("Paste fixture text");
        paste.setAllCaps(false);
        paste.setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            InputConnection connection = getCurrentInputConnection();
            boolean requested = false;
            if (clipboard != null && connection != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ESuite fixture", "Esuite42"));
                requested = connection.performContextMenuAction(android.R.id.paste);
            }
            paste.setContentDescription(requested ? "Input accepted" : "Input rejected");
            Log.i(TAG, "Paste key: input connection=" + (connection != null) + ", requested=" + requested);
        });
        LinearLayout keys = new LinearLayout(this);
        keys.addView(key, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        keys.addView(paste, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return keys;
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return false;
    }
}
