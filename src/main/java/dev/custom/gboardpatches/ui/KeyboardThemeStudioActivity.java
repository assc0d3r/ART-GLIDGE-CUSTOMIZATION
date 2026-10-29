package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KeyboardThemeStudioActivity extends Activity {
    public static final String PREF_KEY_STUDIO = "pref_key_gboard_theme_studio";
    private static final String EXTRA_ACTION = "morphe.theme.action";
    private static final int REQUEST_IMPORT_THEME = 7410;
    private static final int REQUEST_EXPORT_THEME = 7411;
    private static final int MAX_HISTORY = 30;
    private static final String[] EDITABLE_COLORS = {
        "boardBackground", "keyBackground", "keyText", "modifierKeyBackground",
        "modifierKeyText", "enterKeyBackground", "enterKeyText", "pressedKeyBackground",
        "accent", "keyBorderColor"
    };
    private static final String[] COLOR_LABELS = {
        "Board", "Letter keys", "Key text", "Modifier keys", "Modifier text",
        "Enter key", "Enter text", "Pressed key", "Accent", "Key border"
    };
    private static final String[] SHAPES = {
        "ROUNDED", "SHARP", "PILL", "CUT", "SQUIRCLE", "ARCH",
        "LEAF", "SLANT", "HEXAGON", "SCALLOP", "TICKET", "CIRCLE"
    };

    private LinearLayout content;
    private Map<String, Object> theme;
    private Map<String, Object> editedLook;
    private int selectedVariant = -1;
    private final List<String> history = new ArrayList<>();
    private int historyPosition = -1;
    private ThemePreview preview;
    private boolean editorMode;
    private boolean launchedForEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editorMode = "edit".equals(getIntent().getStringExtra(EXTRA_ACTION));
        launchedForEdit = editorMode;
        if (editorMode) {
            try {
                theme = ThemeStore.load(this, getIntent().getStringExtra("theme_id"));
                editedLook = theme;
                pushHistory();
                showEditor();
            } catch (IOException exception) {
                showMessage(exception.getMessage());
                showManager();
            }
        } else {
            showManager();
        }
    }

    @Override
    public void onBackPressed() {
        if (editorMode) {
            if (launchedForEdit) finish();
            else showManager();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQUEST_IMPORT_THEME || requestCode == REQUEST_EXPORT_THEME) showManager();
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT_THEME) {
            importTheme(uri);
        } else if (requestCode == REQUEST_EXPORT_THEME) {
            exportTheme(uri);
        }
    }

    private void showManager() {
        editorMode = false;
        setTitle("Gboard Theme Studio");
        ScrollView scroll = new ScrollView(this);
        content = column();
        scroll.addView(content);
        TextView heading = text("Gboard Theme Studio", 24);
        content.addView(heading);
        content.addView(text(
            "Create looks, tune key colors and shapes, and import or export WMKeyboard theme files.",
            14
        ));

        Button create = button("Create theme");
        create.setOnClickListener(view -> createTheme("Ocean"));
        content.addView(create);

        Button importButton = button("Import .wmtheme.json");
        importButton.setOnClickListener(view -> startImport());
        content.addView(importButton);

        Button useDefault = button("Use Default (system)");
        useDefault.setOnClickListener(view -> {
            ThemeStore.setActive(this, "builtin_default");
            showManager();
        });
        content.addView(useDefault);

        TextView presetsHeading = text("Start from a preset", 18);
        content.addView(presetsHeading);
        for (String preset : ThemeStore.presetNames()) {
            Button presetButton = button("Create " + preset);
            presetButton.setOnClickListener(view -> createTheme(preset));
            content.addView(presetButton);
        }

        content.addView(text("Your themes", 20));
        String activeId = ThemeStore.activeId(this);
        for (Map<String, Object> savedTheme : ThemeStore.list(this)) {
            String id = ThemeDocument.string(savedTheme, "id", "");
            String name = ThemeDocument.string(savedTheme, "name", "Untitled");
            LinearLayout card = column();
            card.setBackgroundColor(0xFFE7E8EC);
            TextView title = text(name + (id.equals(activeId) ? "  • Active" : ""), 17);
            title.setTextColor(0xFF17181C);
            card.addView(title);
            LinearLayout actions = row();
            Button apply = smallButton("Use");
            apply.setOnClickListener(view -> {
                ThemeStore.setActive(this, id);
                showManager();
            });
            Button edit = smallButton("Edit");
            edit.setOnClickListener(view -> openEditor(id));
            Button export = smallButton("Export");
            export.setOnClickListener(view -> startExport(savedTheme));
            Button duplicate = smallButton("Copy");
            duplicate.setOnClickListener(view -> duplicateTheme(savedTheme));
            Button delete = smallButton("Delete");
            delete.setOnClickListener(view -> confirmDelete(id, name));
            actions.addView(apply);
            actions.addView(edit);
            actions.addView(export);
            actions.addView(duplicate);
            actions.addView(delete);
            card.addView(actions);
            content.addView(card);
        }
        setContentView(scroll);
    }

    private void showEditor() {
        editorMode = true;
        setTitle(ThemeDocument.string(theme, "name", "Theme"));
        ScrollView scroll = new ScrollView(this);
        content = column();
        scroll.addView(content);

        LinearLayout header = row();
        Button back = smallButton("‹ Themes");
        back.setOnClickListener(view -> {
            if (launchedForEdit) finish();
            else showManager();
        });
        header.addView(back);
        Button undo = smallButton("Undo");
        undo.setEnabled(historyPosition > 0);
        undo.setOnClickListener(view -> moveHistory(-1));
        header.addView(undo);
        Button redo = smallButton("Redo");
        redo.setEnabled(historyPosition + 1 < history.size());
        redo.setOnClickListener(view -> moveHistory(1));
        header.addView(redo);
        content.addView(header);

        preview = new ThemePreview(this, editedLook);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(150)
        );
        content.addView(preview, previewParams);

        Button rename = button("Theme name: " + ThemeDocument.string(theme, "name", "My theme"));
        rename.setOnClickListener(view -> editThemeName());
        content.addView(rename);

        Button group = button("Group name: " + ThemeDocument.string(theme, "familyName", "Not set"));
        group.setOnClickListener(view -> editGroupName());
        content.addView(group);

        LinearLayout looks = row();
        looks.addView(text("Looks", 18));
        addLookChip(looks, -1, "1");
        List<Object> variants = ThemeDocument.array(theme.get("variants"));
        for (int i = 0; i < variants.size(); i++) {
            Map<String, Object> variant = ThemeDocument.object(variants.get(i));
            addLookChip(looks, i, ThemeDocument.string(variant, "name", "Look " + (i + 2)));
        }
        Button addLook = smallButton("+ Look");
        addLook.setEnabled(variants.size() < 12);
        addLook.setOnClickListener(view -> addLook());
        looks.addView(addLook);
        if (selectedVariant >= 0) {
            Button removeLook = smallButton("Delete look");
            removeLook.setOnClickListener(view -> deleteLook());
            looks.addView(removeLook);
        }
        content.addView(looks);

        Button renameLook = button("Look name: " + ThemeDocument.string(editedLook, "name", "My theme"));
        renameLook.setOnClickListener(view -> editLookName());
        content.addView(renameLook);

        content.addView(text("Colors", 20));
        for (int i = 0; i < EDITABLE_COLORS.length; i++) {
            addColorControl(EDITABLE_COLORS[i], COLOR_LABELS[i]);
        }
        content.addView(text("Key shape", 20));
        Spinner shape = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            SHAPES
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shape.setAdapter(adapter);
        String currentShape = ThemeDocument.string(editedLook, "keyShape", "ROUNDED");
        int shapeIndex = 0;
        for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i].equalsIgnoreCase(currentShape)) shapeIndex = i;
        shape.setSelection(shapeIndex);
        shape.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean initialized;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initialized) {
                    initialized = true;
                    return;
                }
                pushHistory();
                editedLook.put("keyShape", SHAPES[position]);
                persistTheme();
                preview.invalidate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        content.addView(shape);

        content.addView(text("Key corner radius", 16));
        SeekBar radius = new SeekBar(this);
        radius.setMax(28);
        radius.setProgress((int) ThemeDocument.number(editedLook, "keyCornerRadiusDp", 6));
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean changed;
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (!changed) {
                    pushHistory();
                    changed = true;
                }
                editedLook.put("keyCornerRadiusDp", (long) progress);
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (changed) persistTheme();
            }
        });
        content.addView(radius);

        Button save = button("Apply theme and finish");
        save.setOnClickListener(view -> {
            persistTheme();
            ThemeStore.setActive(this, ThemeDocument.string(theme, "id", ""));
            showManager();
        });
        content.addView(save);
        setContentView(scroll);
    }

    private void addColorControl(String key, String label) {
        int color = themeColor(key);
        Button control = button(label + "  " + String.format("#%08X", color));
        control.setOnClickListener(view -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setHint("#AARRGGBB");
            input.setText(String.format("%08X", themeColor(key)));
            new AlertDialog.Builder(this)
                .setTitle(label)
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> setThemeColor(key, input.getText().toString()))
                .show();
        });
        content.addView(control);
    }

    private void setThemeColor(String key, String raw) {
        String hex = raw.trim().replace("#", "");
        if (hex.length() == 6) hex = "FF" + hex;
        if (hex.length() != 8) {
            showMessage("Enter a six- or eight-digit hexadecimal color.");
            return;
        }
        try {
            long color = Long.parseLong(hex, 16);
            pushHistory();
            editedLook.put(key, color);
            persistTheme();
            showEditor();
        } catch (NumberFormatException exception) {
            showMessage("That color is not valid hexadecimal.");
        }
    }

    private int themeColor(String key) {
        if (editedLook.get(key) != null) {
            return ThemeDocument.color(editedLook, key, 0xFF17181C);
        }
        if ("modifierKeyText".equals(key) || "enterKeyText".equals(key)) {
            return ThemeDocument.color(editedLook, "keyText", 0xFFE9E9EE);
        }
        if ("pressedKeyBackground".equals(key)) {
            return ThemeDocument.color(editedLook, "accent", 0xFF8AB4F8);
        }
        return 0xFF17181C;
    }

    private void createTheme(String preset) {
        theme = ThemeStore.create(this, preset);
        editedLook = theme;
        selectedVariant = -1;
        history.clear();
        historyPosition = -1;
        pushHistory();
        try {
            ThemeStore.save(this, theme);
            ThemeStore.setActive(this, ThemeDocument.string(theme, "id", ""));
            showEditor();
        } catch (IOException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void openEditor(String id) {
        ThemeStore.setActive(this, id);
        Intent intent = new Intent(this, KeyboardThemeStudioActivity.class);
        intent.putExtra(EXTRA_ACTION, "edit");
        intent.putExtra("theme_id", id);
        startActivity(intent);
    }

    private void editThemeName() {
        editText("Theme name", ThemeDocument.string(theme, "name", ""), value -> {
            pushHistory();
            theme.put("name", value.trim());
            persistTheme();
            showEditor();
        });
    }

    private void editLookName() {
        editText("Look name", ThemeDocument.string(editedLook, "name", ""), value -> {
            pushHistory();
            editedLook.put("name", value.trim());
            persistTheme();
            showEditor();
        });
    }

    private void editGroupName() {
        editText("Group name", ThemeDocument.string(theme, "familyName", ""), value -> {
            pushHistory();
            if (value.trim().isEmpty()) theme.remove("familyName");
            else theme.put("familyName", value.trim());
            persistTheme();
            showEditor();
        });
    }

    private interface TextApplyListener {
        void apply(String value);
    }

    private void editText(String title, String value, TextApplyListener onApply) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", (dialog, which) -> onApply.apply(input.getText().toString()))
            .show();
    }

    private void addLook() {
        List<Object> variants = ThemeDocument.array(theme.get("variants"));
        if (variants.size() >= 12) return;
        try {
            pushHistory();
            Map<String, Object> copy = ThemeDocument.copy(editedLook);
            copy.put("id", "custom_" + java.util.UUID.randomUUID().toString().replace("-", ""));
            copy.put("name", ThemeDocument.string(editedLook, "name", "Theme") + " look");
            copy.remove("variants");
            variants.add(copy);
            theme.put("variants", variants);
            selectedVariant = variants.size() - 1;
            editedLook = copy;
            persistTheme();
            showEditor();
        } catch (IOException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void deleteLook() {
        if (selectedVariant < 0) return;
        pushHistory();
        List<Object> variants = ThemeDocument.array(theme.get("variants"));
        if (selectedVariant < variants.size()) variants.remove(selectedVariant);
        if (variants.isEmpty()) theme.remove("variants");
        selectedVariant = -1;
        editedLook = theme;
        persistTheme();
        showEditor();
    }

    private void addLookChip(LinearLayout parent, int index, String label) {
        Button chip = smallButton((selectedVariant == index ? "● " : "") + label);
        chip.setOnClickListener(view -> {
            selectedVariant = index;
            editedLook = index < 0 ? theme : ThemeDocument.object(ThemeDocument.array(theme.get("variants")).get(index));
            showEditor();
        });
        parent.addView(chip);
    }

    private void persistTheme() {
        try {
            ThemeStore.save(this, theme);
            if (ThemeDocument.string(theme, "id", "").equals(ThemeStore.activeId(this))) {
                ThemeStore.setActive(this, ThemeStore.activeId(this));
            }
        } catch (IOException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void pushHistory() {
        if (theme == null) return;
        try {
            String snapshot = ThemeDocument.write(theme);
            while (history.size() > historyPosition + 1) history.remove(history.size() - 1);
            if (history.isEmpty() || !snapshot.equals(history.get(history.size() - 1))) {
                history.add(snapshot);
                if (history.size() > MAX_HISTORY) history.remove(0);
                historyPosition = history.size() - 1;
            }
        } catch (IOException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void moveHistory(int direction) {
        int next = historyPosition + direction;
        if (next < 0 || next >= history.size()) return;
        try {
            theme = ThemeDocument.parse(history.get(next));
            historyPosition = next;
            editedLook = selectedVariant < 0 ? theme :
                ThemeDocument.object(ThemeDocument.array(theme.get("variants")).get(selectedVariant));
            persistTheme();
            showEditor();
        } catch (IOException | IndexOutOfBoundsException exception) {
            showMessage("The editor history could not be restored.");
        }
    }

    private void confirmDelete(String id, String name) {
        new AlertDialog.Builder(this)
            .setTitle("Delete theme?")
            .setMessage("Delete " + name + "?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (dialog, which) -> {
                try {
                    ThemeStore.delete(this, id);
                    showManager();
                } catch (IOException exception) {
                    showMessage(exception.getMessage());
                }
            })
            .show();
    }

    private void duplicateTheme(Map<String, Object> source) {
        try {
            Map<String, Object> copy = ThemeStore.duplicate(this, source);
            ThemeStore.setActive(this, ThemeDocument.string(copy, "id", ""));
            openEditor(ThemeDocument.string(copy, "id", ""));
        } catch (IOException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void startImport() {
        Intent picker = new Intent("android.intent.action.OPEN_DOCUMENT");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/json");
        try {
            startActivityForResult(picker, REQUEST_IMPORT_THEME);
        } catch (android.content.ActivityNotFoundException exception) {
            showMessage("No file picker is available.");
        }
    }

    private void importTheme(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The selected theme file could not be opened.");
            Map<String, Object> imported = ThemeStore.importTheme(this, ThemeDocument.read(input));
            showMessage("Imported and applied " + ThemeDocument.string(imported, "name", "theme") + ".");
            showManager();
        } catch (IOException | SecurityException exception) {
            showMessage(exception.getMessage());
            showManager();
        }
    }

    private void startExport(Map<String, Object> selected) {
        try {
            theme = ThemeStore.exportCopy(selected);
            Intent create = new Intent("android.intent.action.CREATE_DOCUMENT");
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("application/json");
            String name = ThemeDocument.string(theme, "name", "Gboard theme")
                .replaceAll("[\\\\/:*?\"<>|]", "_");
            create.putExtra("android.intent.extra.TITLE", name + ".wmtheme.json");
            startActivityForResult(create, REQUEST_EXPORT_THEME);
        } catch (IOException | android.content.ActivityNotFoundException exception) {
            showMessage("Unable to start theme export.");
        }
    }

    private void exportTheme(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("The export file could not be opened.");
            output.write(ThemeDocument.write(theme).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            showMessage("Theme exported.");
        } catch (IOException | SecurityException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message == null ? "Theme operation failed." : message, Toast.LENGTH_LONG).show();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(20));
        layout.setBackgroundColor(0xFFF4F5F8);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF17181C);
        view.setPadding(dp(6), dp(8), dp(6), dp(8));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        return button;
    }

    private Button smallButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ThemePreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<String, Object> previewTheme;

        ThemePreview(Activity context, Map<String, Object> previewTheme) {
            super(context);
            this.previewTheme = previewTheme;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int board = themeColor("boardBackground");
            paint.setColor(board);
            canvas.drawColor(board);
            int width = getWidth();
            int height = getHeight();
            int key = themeColor("keyBackground");
            int textColor = themeColor("keyText");
            float radius = Math.max(0, Math.min(height / 6f, (float) ThemeDocument.number(previewTheme, "keyCornerRadiusDp", 6) * getResources().getDisplayMetrics().density));
            float left = dp(12);
            float top = height * 0.28f;
            float keyWidth = (width - dp(36)) / 5f;
            float keyHeight = height * 0.32f;
            for (int i = 0; i < 5; i++) {
                float x = left + i * (keyWidth + dp(3));
                paint.setColor(i == 4 ? themeColor("enterKeyBackground") : key);
                canvas.drawRoundRect(new RectF(x, top, x + keyWidth, top + keyHeight), radius, radius, paint);
                paint.setColor(textColor);
                paint.setTextSize(dp(18));
                canvas.drawText(String.valueOf((char) ('A' + i)), x + keyWidth * 0.42f, top + keyHeight * 0.62f, paint);
            }
            paint.setColor(themeColor("modifierKeyBackground"));
            canvas.drawRoundRect(new RectF(left, top + keyHeight + dp(6), width - left, height - dp(10)),
                radius, radius, paint);
        }
    }
}
