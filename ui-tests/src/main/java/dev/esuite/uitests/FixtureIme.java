package dev.esuite.uitests;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;

/** Deterministic software keyboard for disposable emulators. */
public final class FixtureIme extends InputMethodService {
    @Override public View onCreateInputView() {
        Button key = new Button(this);
        key.setText("Type fixture text");
        key.setAllCaps(false);
        key.setOnClickListener(view -> {
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) {
                connection.setComposingText("Es", 1);
                connection.commitText("Esuite42", 1);
            }
        });
        Button paste = new Button(this);
        paste.setText("Paste fixture text");
        paste.setAllCaps(false);
        paste.setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            InputConnection connection = getCurrentInputConnection();
            if (clipboard != null && connection != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ESuite fixture", "Esuite42"));
                connection.performContextMenuAction(android.R.id.paste);
            }
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
