package dev.custom.gboardpatches.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class KeyboardMacroManager {
    private static final String TAG = "XGesture";
    private static final String PREF_DEFINITIONS = "pref_key_xgesture_macro_definitions";
    private static final int MAX_DEFINITIONS = 64;

    private KeyboardMacroManager() {}

    public static void show(Context context) {
        List<Definition> definitions = readDefinitions(context);
        String[] labels = new String[definitions.size()];
        for (int index = 0; index < definitions.size(); index++) {
            Definition definition = definitions.get(index);
            labels[index] = (definition.macro ? "Macro: " : "Snippet: ") + definition.trigger;
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("Snippets & macros")
            .setItems(labels, (ignored, index) -> edit(context, definitions.get(index)))
            .setNeutralButton("Add snippet", null)
            .setPositiveButton("Add macro", null)
            .setNegativeButton("Close", null)
            .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                edit(context, null, false)
            );
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                edit(context, null, true)
            );
        });
        dialog.show();
    }

    public static boolean expandIfMatched(Context context, InputConnection connection) {
        if (context == null || connection == null) return false;
        CharSequence before = connection.getTextBeforeCursor(512, 0);
        if (before == null || before.length() == 0) return false;

        String text = before.toString();
        List<Definition> definitions = readDefinitions(context);
        for (Definition definition : definitions) {
            int start = text.length() - definition.trigger.length();
            if (start < 0 || !text.regionMatches(start, definition.trigger, 0, definition.trigger.length())) {
                continue;
            }
            if (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) continue;

            connection.deleteSurroundingText(definition.trigger.length(), 0);
            if (!definition.macro) {
                connection.commitText(definition.value, 1);
            } else if (!runMacro(context, connection, definition.value)) {
                connection.commitText(definition.trigger, 1);
                return false;
            }
            return true;
        }
        return false;
    }

    private static boolean runMacro(Context context, InputConnection connection, String actions) {
        for (String action : actions.split(",")) {
            String command = action.trim();
            if (command.isEmpty()) continue;
            if (command.startsWith("insertText:")) {
                connection.commitText(command.substring("insertText:".length()), 1);
            } else if ("paste".equals(command)) {
                if (!pasteClipboard(connection, context)) return false;
            } else if ("undo".equals(command)) {
                sendModifiedKey(connection, KeyEventCodes.Z, KeyEventCodes.META_CTRL);
            } else if ("redo".equals(command)) {
                sendModifiedKey(connection, KeyEventCodes.Z, KeyEventCodes.META_CTRL | KeyEventCodes.META_SHIFT);
            } else if ("deleteWord".equals(command)) {
                if (!KeyboardGestureRuntime.deleteWord(connection)) return false;
            } else if ("left".equals(command)) {
                sendKey(connection, KeyEventCodes.LEFT);
            } else if ("right".equals(command)) {
                sendKey(connection, KeyEventCodes.RIGHT);
            } else if ("selectAll".equals(command)) {
                connection.performContextMenuAction(android.R.id.selectAll);
            } else if ("copy".equals(command)) {
                connection.performContextMenuAction(android.R.id.copy);
            } else {
                Log.w(TAG, "Ignoring unsupported macro action: " + command);
                return false;
            }
        }
        return true;
    }

    private static boolean pasteClipboard(InputConnection connection, Context context) {
        return KeyboardGestureRuntime.pasteClipboardFromActiveContext(connection, context);
    }

    private static void sendKey(InputConnection connection, int keyCode) {
        connection.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
        connection.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
    }

    private static void sendModifiedKey(InputConnection connection, int keyCode, int modifiers) {
        connection.sendKeyEvent(new android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, modifiers
        ));
        connection.sendKeyEvent(new android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP, keyCode, 0, 0, 0, modifiers
        ));
    }

    private static void edit(Context context, Definition existing) {
        edit(context, existing, existing != null && existing.macro);
    }

    private static void edit(Context context, Definition existing, boolean macro) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, 0);

        TextView storageNotice = new TextView(context);
        storageNotice.setText("Entries are stored in Gboard preferences. Do not use this for passwords or active one-time codes.");
        form.addView(storageNotice, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText trigger = new EditText(context);
        trigger.setSingleLine(true);
        trigger.setHint("Trigger (for example @addr)");
        trigger.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (existing != null) trigger.setText(existing.trigger);
        form.addView(trigger, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText value = new EditText(context);
        value.setHint(macro
            ? "Actions: paste, undo, redo, deleteWord, left, right, selectAll, copy, insertText:text"
            : "Text to insert");
        value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        value.setMinLines(macro ? 2 : 3);
        if (existing != null) value.setText(existing.value);
        form.addView(value, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog editor = new AlertDialog.Builder(context)
            .setTitle(existing == null ? (macro ? "Add macro" : "Add snippet") : "Edit " + (macro ? "macro" : "snippet"))
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setNeutralButton(existing == null ? null : "Delete", null)
            .setPositiveButton("Save", null)
            .create();
        editor.setOnShowListener(ignored -> {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String triggerText = trigger.getText().toString().trim();
                String valueText = value.getText().toString();
                if (!isValid(triggerText, valueText, macro)) {
                    Toast.makeText(context, "Use a non-empty @trigger and valid content.", Toast.LENGTH_LONG).show();
                    return;
                }
                List<Definition> definitions = readDefinitions(context);
                if (existing == null && definitions.size() >= MAX_DEFINITIONS) {
                    Toast.makeText(context, "The limit of 64 snippets and macros has been reached.", Toast.LENGTH_LONG).show();
                    return;
                }
                for (Definition definition : definitions) {
                    if (!definition.trigger.equals(triggerText)) continue;
                    if (existing == null || !definition.trigger.equals(existing.trigger)) {
                        Toast.makeText(context, "That trigger is already in use.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                if (existing != null) {
                    definitions.removeIf(definition -> definition.trigger.equals(existing.trigger));
                }
                definitions.add(new Definition(triggerText, valueText, macro));
                saveDefinitions(context, definitions);
                editor.dismiss();
            });
            if (existing != null) {
                editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    List<Definition> definitions = readDefinitions(context);
                    definitions.removeIf(definition -> definition.trigger.equals(existing.trigger));
                    saveDefinitions(context, definitions);
                    editor.dismiss();
                });
            }
        });
        editor.show();
    }

    private static boolean isValid(String trigger, String value, boolean macro) {
        if (!trigger.startsWith("@") || trigger.length() < 2 || trigger.length() > 64 ||
            trigger.chars().anyMatch(Character::isWhitespace) || value.trim().isEmpty() ||
            value.length() > 4096) {
            return false;
        }
        if (!macro) return true;
        for (String action : value.split(",")) {
            String command = action.trim();
            if (!command.startsWith("insertText:") &&
                !"paste".equals(command) && !"undo".equals(command) && !"redo".equals(command) &&
                !"deleteWord".equals(command) && !"left".equals(command) && !"right".equals(command) &&
                !"selectAll".equals(command) && !"copy".equals(command)) {
                return false;
            }
        }
        return true;
    }

    private static List<Definition> readDefinitions(Context context) {
        List<Definition> definitions = new ArrayList<>();
        try {
            SharedPreferences preferences = GlideTrailPreferences.getPrefs(context);
            String[] records = preferences.getString(PREF_DEFINITIONS, "").split("\n");
            for (int index = 0; index < records.length && index < MAX_DEFINITIONS; index++) {
                String[] fields = records[index].split("\\.", 3);
                if (fields.length != 3 || (!"s".equals(fields[1]) && !"m".equals(fields[1]))) continue;
                String trigger = decode(fields[0]);
                String value = decode(fields[2]);
                boolean macro = "m".equals(fields[1]);
                if (trigger != null && value != null && isValid(trigger, value, macro)) {
                    definitions.add(new Definition(trigger, value, macro));
                }
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to load saved snippets and macros.", exception);
        }
        return definitions;
    }

    private static void saveDefinitions(Context context, List<Definition> definitions) {
        StringBuilder values = new StringBuilder();
        for (Definition definition : definitions) {
            if (values.length() > 0) values.append('\n');
            values.append(encode(definition.trigger))
                .append('.')
                .append(definition.macro ? 'm' : 's')
                .append('.')
                .append(encode(definition.value));
        }
        try {
            GlideTrailPreferences.getPrefs(context).edit()
                .putString(PREF_DEFINITIONS, values.toString())
                .apply();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to save snippets and macros.", exception);
        }
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String decode(String value) {
        try {
            return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Ignoring a malformed saved snippet or macro.", exception);
            return null;
        }
    }

    private static final class Definition {
        final String trigger;
        final String value;
        final boolean macro;

        Definition(String trigger, String value, boolean macro) {
            this.trigger = trigger;
            this.value = value;
            this.macro = macro;
        }
    }

    private static final class KeyEventCodes {
        static final int Z = android.view.KeyEvent.KEYCODE_Z;
        static final int LEFT = android.view.KeyEvent.KEYCODE_DPAD_LEFT;
        static final int RIGHT = android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        static final int META_CTRL = android.view.KeyEvent.META_CTRL_ON;
        static final int META_SHIFT = android.view.KeyEvent.META_SHIFT_ON;
    }
}
