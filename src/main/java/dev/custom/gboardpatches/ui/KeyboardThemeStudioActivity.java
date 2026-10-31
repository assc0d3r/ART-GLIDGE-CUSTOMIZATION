package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
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
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KeyboardThemeStudioActivity extends Activity {
    public static final String PREF_KEY_STUDIO = "pref_key_gboard_theme_studio";
    private static final String EXTRA_ACTION = "morphe.theme.action";
    private static final int REQUEST_IMPORT_THEME = 7410;
    private static final int REQUEST_EXPORT_THEME = 7411;
    private static final int REQUEST_IMPORT_ICONS = 7412;
    private static final int REQUEST_BACKGROUND_IMAGE = 7413;
    private static final int REQUEST_KEY_TEXTURE = 7414;
    private static final int REQUEST_STICKER_IMAGE = 7415;
    private static final int REQUEST_KEY_STYLE_IMAGE = 7416;
    private static final int MAX_THEME_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_HISTORY = 30;
    private static final String[] EDITABLE_COLORS = {
        "boardBackground", "keyBackground", "keyText", "modifierKeyBackground",
        "modifierKeyText", "enterKeyBackground", "enterKeyText", "pressedKeyBackground",
        "accent", "keyBorderColor", "toolbarBackground", "toolbarToolBackground",
        "toolbarToolIcon", "suggestionStripBackground", "suggestionText",
        "popupBackground", "popupText", "popupOutline"
    };
    private static final String[] COLOR_LABELS = {
        "Board", "Letter keys", "Key text", "Modifier keys", "Modifier text",
        "Enter key", "Enter text", "Pressed key", "Accent", "Key border",
        "Toolbar", "Toolbar tools", "Toolbar icons", "Suggestion strip", "Suggestion text",
        "Popup background", "Popup text", "Popup outline"
    };
    private static final String[] SHAPES = {
        "ROUNDED", "SHARP", "PILL", "CUT", "SQUIRCLE", "ARCH",
        "LEAF", "SLANT", "HEXAGON", "SCALLOP", "TICKET", "CIRCLE"
    };
    private static final String[] BACKGROUND_ANIMATIONS = { "NONE", "FLOW", "HUE_CYCLE" };
    private static final String[] TEXTURE_FITS = { "CROP", "STRETCH", "TILE" };
    private static final String[] POPUP_SHAPES = {
        "AUTO", "ROUNDED", "SHARP", "PILL", "CUT", "SQUIRCLE", "ARCH",
        "LEAF", "SLANT", "HEXAGON", "SCALLOP", "TICKET", "CIRCLE"
    };
    private static final String[] KEY_STYLE_SLOTS = buildKeyStyleSlots();

    private LinearLayout content;
    private Map<String, Object> theme;
    private Map<String, Object> editedLook;
    private int selectedVariant = -1;
    private final List<String> history = new ArrayList<>();
    private int historyPosition = -1;
    private ThemePreview preview;
    private boolean editorMode;
    private boolean launchedForEdit;
    private String pendingKeyStyleSlot;

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
        } else if (requestCode == REQUEST_IMPORT_ICONS) {
            importIcons(uri);
        } else if (requestCode == REQUEST_BACKGROUND_IMAGE) {
            importThemeImage(uri, "backgroundImageBase64", "Background image");
        } else if (requestCode == REQUEST_KEY_TEXTURE) {
            importThemeImage(uri, "keyTextureBase64", "Key texture");
        } else if (requestCode == REQUEST_STICKER_IMAGE) {
            importSticker(uri);
        } else if (requestCode == REQUEST_KEY_STYLE_IMAGE) {
            importKeyStyleImage(uri);
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
            "Create themes and apply colors through Gboard's native ZIP-theme system. Native activation currently requires Gboard 18.5.4.",
            14
        ));

        Button create = button("Create theme");
        create.setOnClickListener(view -> createTheme("Ocean"));
        content.addView(create);

        Button importButton = button("Import .wmtheme.json");
        importButton.setOnClickListener(view -> startImport());
        content.addView(importButton);

        Button useDefault = button("Use Default (system)");
        useDefault.setOnClickListener(view -> activateTheme("builtin_default"));
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
            apply.setOnClickListener(view -> activateTheme(id));
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

        LinearLayout iconRow = row();
        int iconCount = KeyboardIconPackRuntime.activeIconCount(this);
        iconRow.addView(text("Imported icons: " + iconCount, 16));
        Button importIcons = smallButton("Import icon pack");
        importIcons.setOnClickListener(view -> startIconImport());
        iconRow.addView(importIcons);
        content.addView(iconRow);

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

        content.addView(text("Popup style", 20));
        Spinner popupShape = new Spinner(this);
        ArrayAdapter<String> popupAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            POPUP_SHAPES
        );
        popupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        popupShape.setAdapter(popupAdapter);
        String currentPopupShape = ThemeDocument.string(editedLook, "popupShape", "AUTO");
        for (int index = 0; index < POPUP_SHAPES.length; index++) {
            if (POPUP_SHAPES[index].equalsIgnoreCase(currentPopupShape)) popupShape.setSelection(index);
        }
        popupShape.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean initialized;
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initialized) {
                    initialized = true;
                    return;
                }
                pushHistory();
                if ("AUTO".equals(POPUP_SHAPES[position])) editedLook.remove("popupShape");
                else editedLook.put("popupShape", POPUP_SHAPES[position]);
                persistTheme();
                preview.invalidate();
            }
        });
        content.addView(text("Popup shape", 14));
        content.addView(popupShape);
        addNumberControl("popupCornerRadiusDp", "Popup corner radius", 0, 40, 12);
        addNumberControl("popupOutlineWidthDp", "Popup outline width", 0, 3, 0);

        content.addView(text("Images and textures", 20));
        Button backgroundImage = button(editedLook.containsKey("backgroundImageBase64")
            ? "Replace background image" : "Add background image");
        backgroundImage.setOnClickListener(view -> startImageImport(REQUEST_BACKGROUND_IMAGE));
        content.addView(backgroundImage);
        if (editedLook.containsKey("backgroundImageBase64")) {
            Button clearBackground = smallButton("Remove background image");
            clearBackground.setOnClickListener(view -> clearThemeImage("backgroundImageBase64"));
            content.addView(clearBackground);
            addNumberControl("backgroundImageOpacity", "Background image opacity", 0, 100, 100);
        }
        Button keyTexture = button(editedLook.containsKey("keyTextureBase64")
            ? "Replace key texture" : "Add key texture");
        keyTexture.setOnClickListener(view -> startImageImport(REQUEST_KEY_TEXTURE));
        content.addView(keyTexture);
        if (editedLook.containsKey("keyTextureBase64")) {
            Button clearTexture = smallButton("Remove key texture");
            clearTexture.setOnClickListener(view -> clearThemeImage("keyTextureBase64"));
            content.addView(clearTexture);
            addNumberControl("keyTextureOpacity", "Key texture opacity", 0, 100, 100);
            Spinner textureFit = new Spinner(this);
            ArrayAdapter<String> fitAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                TEXTURE_FITS
            );
            fitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            textureFit.setAdapter(fitAdapter);
            String currentFit = ThemeDocument.string(editedLook, "keyTextureFit", "CROP");
            for (int index = 0; index < TEXTURE_FITS.length; index++) {
                if (TEXTURE_FITS[index].equals(currentFit)) textureFit.setSelection(index);
            }
            textureFit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                private boolean initialized;
                @Override public void onNothingSelected(AdapterView<?> parent) {}
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!initialized) {
                        initialized = true;
                        return;
                    }
                    pushHistory();
                    editedLook.put("keyTextureFit", TEXTURE_FITS[position]);
                    persistTheme();
                    preview.invalidate();
                }
            });
            content.addView(text("Key texture fit", 14));
            content.addView(textureFit);
        }
        Button followWallpaper = button(Boolean.TRUE.equals(editedLook.get("followWallpaperColors"))
            ? "Follow wallpaper colors: on" : "Follow wallpaper colors: off");
        followWallpaper.setOnClickListener(view -> {
            pushHistory();
            editedLook.put("followWallpaperColors",
                !Boolean.TRUE.equals(editedLook.get("followWallpaperColors")));
            persistTheme();
            showEditor();
        });
        content.addView(followWallpaper);
        content.addView(text("Animated background", 16));
        Spinner animation = new Spinner(this);
        ArrayAdapter<String> animationAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            BACKGROUND_ANIMATIONS
        );
        animationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        animation.setAdapter(animationAdapter);
        String currentAnimation = ThemeDocument.string(editedLook, "backgroundAnimation", "NONE");
        for (int index = 0; index < BACKGROUND_ANIMATIONS.length; index++) {
            if (BACKGROUND_ANIMATIONS[index].equals(currentAnimation)) animation.setSelection(index);
        }
        animation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean initialized;
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initialized) {
                    initialized = true;
                    return;
                }
                pushHistory();
                editedLook.put("backgroundAnimation", BACKGROUND_ANIMATIONS[position]);
                persistTheme();
                preview.invalidate();
            }
        });
        content.addView(animation);
        if (!"NONE".equals(currentAnimation)) {
            addNumberControl("backgroundAnimationSpeed", "Animation speed (x0.25)", 0, 11, 3);
        }

        content.addView(text("Stickers", 20));
        List<Object> stickers = ThemeDocument.array(editedLook.get("stickers"));
        for (int index = 0; index < stickers.size(); index++) {
            final int stickerIndex = index;
            LinearLayout stickerRow = row();
            Button editSticker = smallButton("Sticker " + (index + 1) + " placement");
            editSticker.setOnClickListener(view -> editSticker(stickerIndex));
            stickerRow.addView(editSticker);
            Button removeSticker = smallButton("Remove");
            removeSticker.setOnClickListener(view -> {
                pushHistory();
                stickers.remove(stickerIndex);
                if (stickers.isEmpty()) editedLook.remove("stickers");
                persistTheme();
                showEditor();
            });
            stickerRow.addView(removeSticker);
            content.addView(stickerRow);
        }
        Button addSticker = button("+ Add sticker image");
        addSticker.setEnabled(stickers.size() < 6);
        addSticker.setOnClickListener(view -> startImageImport(REQUEST_STICKER_IMAGE));
        content.addView(addSticker);

        content.addView(text("Per-key styles", 20));
        content.addView(text(
            "Override the face, label, pressed color, or shape for individual keys.",
            14
        ));
        Map<String, Object> keyStyles = ThemeDocument.object(editedLook.get("singleKeyStyles"));
        for (String slot : new ArrayList<>(keyStyles.keySet())) {
            LinearLayout styleRow = row();
            Button editStyle = smallButton(keyStyleLabel(slot));
            editStyle.setOnClickListener(view -> editSingleKeyStyle(slot));
            styleRow.addView(editStyle);
            Button removeStyle = smallButton("Remove");
            removeStyle.setOnClickListener(view -> {
                pushHistory();
                keyStyles.remove(slot);
                if (keyStyles.isEmpty()) editedLook.remove("singleKeyStyles");
                persistTheme();
                showEditor();
            });
            styleRow.addView(removeStyle);
            content.addView(styleRow);
        }
        Button addKeyStyle = button("+ Add per-key style");
        addKeyStyle.setOnClickListener(view -> editSingleKeyStyle(null));
        content.addView(addKeyStyle);

        Button save = button("Apply theme and finish");
        save.setOnClickListener(view -> {
            if (!persistTheme()) return;
            try {
                ThemeStore.setActive(this, ThemeDocument.string(theme, "id", ""), editedLook);
                showManager();
            } catch (IOException exception) {
                showMessage(exception.getMessage());
            }
        });
        content.addView(save);
        setContentView(scroll);
    }

    private void editSingleKeyStyle(String existingSlot) {
        Map<String, Object> allStyles = ThemeDocument.object(editedLook.get("singleKeyStyles"));
        String currentSlot = existingSlot == null ? KEY_STYLE_SLOTS[0] : existingSlot;
        Map<String, Object> current = ThemeDocument.object(allStyles.get(currentSlot));
        LinearLayout form = column();
        form.setPadding(dp(8), dp(4), dp(8), dp(4));

        Spinner target = new Spinner(this);
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            KEY_STYLE_SLOTS
        );
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        target.setAdapter(targetAdapter);
        int selected = 0;
        for (int index = 0; index < KEY_STYLE_SLOTS.length; index++) {
            if (KEY_STYLE_SLOTS[index].equals(currentSlot)) selected = index;
        }
        target.setSelection(selected);
        if (existingSlot != null) target.setEnabled(false);
        form.addView(target);

        EditText background = colorField(current, "backgroundColor");
        EditText label = colorField(current, "textColor");
        EditText pressed = colorField(current, "pressedColor");
        EditText borderColor = colorField(current, "borderColor");
        EditText borderWidth = numberField(ThemeDocument.number(current, "borderWidthDp", 0));
        if (ThemeDocument.number(current, "borderWidthDp", 0) <= 0) borderWidth.setText("");
        form.addView(text("Background color (blank follows theme)", 14));
        form.addView(background);
        form.addView(text("Label color", 14));
        form.addView(label);
        form.addView(text("Pressed color", 14));
        form.addView(pressed);
        form.addView(text("Border color", 14));
        form.addView(borderColor);
        form.addView(text("Border width in dp (0-3)", 14));
        form.addView(borderWidth);

        Spinner shape = new Spinner(this);
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            SHAPES
        );
        shapeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shape.setAdapter(shapeAdapter);
        String currentShape = ThemeDocument.string(current, "keyShape", "");
        int shapeIndex = 0;
        for (int index = 0; index < SHAPES.length; index++) {
            if (SHAPES[index].equalsIgnoreCase(currentShape)) shapeIndex = index;
        }
        final int defaultShapeIndex = shapeIndex;
        shape.setSelection(shapeIndex);
        form.addView(text("Key shape", 14));
        form.addView(shape);
        Spinner bold = new Spinner(this);
        String[] boldOptions = { "AUTOMATIC", "BOLD", "NORMAL" };
        ArrayAdapter<String> boldAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            boldOptions
        );
        boldAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bold.setAdapter(boldAdapter);
        Object boldValue = current.get("boldLabel");
        bold.setSelection(boldValue instanceof Boolean
            ? ((Boolean) boldValue ? 1 : 2)
            : 0);
        form.addView(text("Label weight", 14));
        form.addView(bold);
        EditText labelSize = numberField(ThemeDocument.number(current, "labelSizeSp", 0));
        if (ThemeDocument.number(current, "labelSizeSp", 0) <= 0) labelSize.setText("");
        form.addView(text("Label size in sp (blank follows layout)", 14));
        form.addView(labelSize);
        Button keyImage = smallButton(current.get("imageBase64") == null
            ? "Add image for this key" : "Replace image for this key");
        form.addView(keyImage);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
            .setTitle(existingSlot == null ? "Add per-key style" : keyStyleLabel(existingSlot))
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", (dialogInterface, which) -> {
                String slot = KEY_STYLE_SLOTS[target.getSelectedItemPosition()];
                Map<String, Object> style = new java.util.LinkedHashMap<>(
                    ThemeDocument.object(allStyles.get(slot))
                );
                try {
                    putOptionalColor(style, "backgroundColor", background.getText().toString());
                    putOptionalColor(style, "textColor", label.getText().toString());
                    putOptionalColor(style, "pressedColor", pressed.getText().toString());
                    putOptionalColor(style, "borderColor", borderColor.getText().toString());
                } catch (NumberFormatException exception) {
                    showMessage("Enter colors as six or eight hexadecimal digits.");
                    return;
                }
                String rawBorderWidth = borderWidth.getText().toString().trim();
                if (rawBorderWidth.isEmpty()) style.remove("borderWidthDp");
                else {
                    try {
                        long width = Long.parseLong(rawBorderWidth);
                        if (width < 0 || width > 3) throw new NumberFormatException();
                        style.put("borderWidthDp", width);
                    } catch (NumberFormatException exception) {
                        showMessage("Key border width must be between 0 and 3 dp.");
                        return;
                    }
                }
                String selectedShape = SHAPES[shape.getSelectedItemPosition()];
                if (currentShape.isEmpty() && defaultShapeIndex == shape.getSelectedItemPosition()) {
                    style.remove("keyShape");
                } else {
                    style.put("keyShape", selectedShape);
                }
                int boldSelection = bold.getSelectedItemPosition();
                if (boldSelection == 0) style.remove("boldLabel");
                else style.put("boldLabel", boldSelection == 1);
                String rawLabelSize = labelSize.getText().toString().trim();
                if (rawLabelSize.isEmpty()) {
                    style.remove("labelSizeSp");
                } else {
                    try {
                        long size = Long.parseLong(rawLabelSize);
                        if (size < 4 || size > 72) throw new NumberFormatException();
                        style.put("labelSizeSp", size);
                    } catch (NumberFormatException exception) {
                        showMessage("Key label size must be between 4 and 72 sp.");
                        return;
                    }
                }
                pushHistory();
                if (style.isEmpty()) allStyles.remove(slot);
                else allStyles.put(slot, style);
                if (allStyles.isEmpty()) editedLook.remove("singleKeyStyles");
                else editedLook.put("singleKeyStyles", allStyles);
                persistTheme();
                showEditor();
            });
        if (existingSlot != null) {
            dialogBuilder.setNeutralButton("Remove", (ignored, which) -> {
                pushHistory();
                allStyles.remove(existingSlot);
                if (allStyles.isEmpty()) editedLook.remove("singleKeyStyles");
                persistTheme();
                showEditor();
            });
        }
        AlertDialog shownDialog = dialogBuilder.show();
        keyImage.setOnClickListener(view -> {
            pendingKeyStyleSlot = existingSlot == null
                ? KEY_STYLE_SLOTS[target.getSelectedItemPosition()]
                : existingSlot;
            shownDialog.dismiss();
            startImageImport(REQUEST_KEY_STYLE_IMAGE);
        });
    }

    private EditText colorField(Map<String, Object> style, String key) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        Object value = style.get(key);
        if (value != null) {
            field.setText(String.format("#%08X", ThemeDocument.color(style, key, 0xFF000000)));
        }
        field.setHint("#AARRGGBB");
        return field;
    }

    private void putOptionalColor(Map<String, Object> style, String key, String raw) {
        String value = raw.trim().replace("#", "");
        if (value.isEmpty()) {
            style.remove(key);
            return;
        }
        if (value.length() == 6) value = "FF" + value;
        if (value.length() != 8) throw new NumberFormatException();
        style.put(key, Long.parseLong(value, 16));
    }

    private static String[] buildKeyStyleSlots() {
        List<String> slots = new ArrayList<>();
        for (char letter = 'a'; letter <= 'z'; letter++) slots.add("key.letter_" + letter);
        for (int digit = 0; digit <= 9; digit++) slots.add("key.number_" + digit);
        String[] special = {
            "key.enter", "key.enter_search", "key.enter_send", "key.enter_done",
            "key.enter_next", "key.enter_previous", "key.enter_go", "key.shift",
            "key.shift_on", "key.shift_lock", "key.backspace", "key.forward_delete",
            "key.tab", "key.globe", "key.input_method_picker", "key.emoji",
            "key.arrow_left", "key.arrow_right", "key.arrow_up", "key.arrow_down"
        };
        slots.addAll(java.util.Arrays.asList(special));
        return slots.toArray(new String[0]);
    }

    private String keyStyleLabel(String slot) {
        if (slot.startsWith("key.letter_") && slot.length() == 12) {
            return "Letter " + slot.charAt(slot.length() - 1);
        }
        if (slot.startsWith("key.number_")) return "Number " + slot.substring("key.number_".length());
        return slot.replace("key.", "").replace('_', ' ');
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
        if ("toolbarBackground".equals(key) || "suggestionStripBackground".equals(key) ||
            "popupBackground".equals(key)) {
            return ThemeDocument.color(editedLook, "boardBackground", 0xFF17181C);
        }
        if ("toolbarToolBackground".equals(key)) {
            return ThemeDocument.color(editedLook, "modifierKeyBackground", 0xFF222428);
        }
        if ("toolbarToolIcon".equals(key) || "suggestionText".equals(key) ||
            "popupText".equals(key)) {
            return ThemeDocument.color(editedLook, "keyText", 0xFFE9E9EE);
        }
        if ("popupOutline".equals(key)) {
            return ThemeDocument.color(editedLook, "keyBorderColor", 0);
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
            showManager();
        }
    }

    private void openEditor(String id) {
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

    private boolean persistTheme() {
        try {
            ThemeStore.save(this, theme);
            return true;
        } catch (IOException exception) {
            showMessage(exception.getMessage());
            return false;
        }
    }

    private void activateTheme(String id) {
        try {
            ThemeStore.setActive(this, id);
            showManager();
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
            showManager();
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

    private void startIconImport() {
        Intent picker = new Intent("android.intent.action.OPEN_DOCUMENT");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(
            "android.intent.extra.MIME_TYPES",
            new String[] { "application/zip", "application/x-zip-compressed", "application/octet-stream" }
        );
        try {
            startActivityForResult(picker, REQUEST_IMPORT_ICONS);
        } catch (android.content.ActivityNotFoundException exception) {
            showMessage("No file picker is available.");
        }
    }

    private void startImageImport(int requestCode) {
        Intent picker = new Intent("android.intent.action.OPEN_DOCUMENT");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        try {
            startActivityForResult(picker, requestCode);
        } catch (android.content.ActivityNotFoundException exception) {
            showMessage("No image picker is available.");
        }
    }

    private void importThemeImage(Uri uri, String field, String label) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The selected image could not be opened.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAX_THEME_IMAGE_BYTES) {
                    throw new IOException("Theme images must be 4 MiB or smaller.");
                }
                output.write(buffer, 0, read);
            }
            byte[] image = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                bounds.outWidth > 8192 || bounds.outHeight > 8192) {
                throw new IOException("The selected file is not a supported image.");
            }
            pushHistory();
            editedLook.put(field, Base64.encodeToString(image, Base64.NO_WRAP));
            if (!persistTheme()) return;
            preview.invalidate();
            showEditor();
            showMessage(label + " added to this theme.");
        } catch (IOException | SecurityException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void clearThemeImage(String field) {
        pushHistory();
        editedLook.remove(field);
        if (persistTheme()) showEditor();
    }

    private void importSticker(Uri uri) {
        List<Object> stickers = ThemeDocument.array(editedLook.get("stickers"));
        if (stickers.size() >= 6) {
            showMessage("A theme can contain at most six stickers.");
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The selected sticker could not be opened.");
            String image = readImageBase64(input);
            Map<String, Object> sticker = new java.util.LinkedHashMap<>();
            sticker.put("imageBase64", image);
            sticker.put("x", 0.5);
            sticker.put("y", 0.5);
            sticker.put("size", 0.18);
            sticker.put("rotation", 0);
            sticker.put("opacity", 100L);
            pushHistory();
            stickers.add(sticker);
            editedLook.put("stickers", stickers);
            if (!persistTheme()) return;
            preview.invalidate();
            showEditor();
        } catch (IOException | SecurityException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void importKeyStyleImage(Uri uri) {
        if (pendingKeyStyleSlot == null) {
            showMessage("No key style is selected for the image.");
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The selected key image could not be opened.");
            String image = readImageBase64(input);
            Map<String, Object> styles = ThemeDocument.object(editedLook.get("singleKeyStyles"));
            Map<String, Object> style = new java.util.LinkedHashMap<>(
                ThemeDocument.object(styles.get(pendingKeyStyleSlot))
            );
            pushHistory();
            style.put("imageBase64", image);
            styles.put(pendingKeyStyleSlot, style);
            editedLook.put("singleKeyStyles", styles);
            if (!persistTheme()) return;
            pendingKeyStyleSlot = null;
            preview.invalidate();
            showEditor();
            showMessage("Key image added.");
        } catch (IOException | SecurityException exception) {
            pendingKeyStyleSlot = null;
            showMessage(exception.getMessage());
        }
    }

    private String readImageBase64(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_THEME_IMAGE_BYTES) {
                throw new IOException("Theme images must be 4 MiB or smaller.");
            }
            output.write(buffer, 0, read);
        }
        byte[] image = output.toByteArray();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > 8192 || bounds.outHeight > 8192) {
            throw new IOException("The selected file is not a supported image.");
        }
        return Base64.encodeToString(image, Base64.NO_WRAP);
    }

    private void editSticker(int index) {
        List<Object> stickers = ThemeDocument.array(editedLook.get("stickers"));
        if (index < 0 || index >= stickers.size()) return;
        Map<String, Object> sticker = ThemeDocument.object(stickers.get(index));
        LinearLayout form = column();
        EditText x = numberField(ThemeDocument.decimal(sticker, "x", 0.5) * 100);
        EditText y = numberField(ThemeDocument.decimal(sticker, "y", 0.5) * 100);
        EditText size = numberField(ThemeDocument.decimal(sticker, "size", 0.18) * 100);
        EditText rotation = numberField(ThemeDocument.decimal(sticker, "rotation", 0));
        EditText opacity = numberField(ThemeDocument.number(sticker, "opacity", 100));
        form.addView(text("Horizontal position (%)", 14));
        form.addView(x);
        form.addView(text("Vertical position (%)", 14));
        form.addView(y);
        form.addView(text("Size (% of keyboard width)", 14));
        form.addView(size);
        form.addView(text("Rotation (degrees)", 14));
        form.addView(rotation);
        form.addView(text("Opacity (%)", 14));
        form.addView(opacity);
        new AlertDialog.Builder(this)
            .setTitle("Sticker placement")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", (dialog, which) -> {
                try {
                    double stickerX = boundedPercent(x, false);
                    double stickerY = boundedPercent(y, false);
                    double stickerSize = boundedPercent(size, true) / 100d;
                    double stickerRotation = Math.max(-180, Math.min(180,
                        Double.parseDouble(rotation.getText().toString().trim())));
                    long stickerOpacity = Math.round(boundedPercent(opacity, true));
                    pushHistory();
                    sticker.put("x", stickerX / 100d);
                    sticker.put("y", stickerY / 100d);
                    sticker.put("size", stickerSize);
                    sticker.put("rotation", stickerRotation);
                    sticker.put("opacity", stickerOpacity);
                    persistTheme();
                    preview.invalidate();
                    showEditor();
                } catch (NumberFormatException exception) {
                    showMessage("Enter valid numeric sticker settings.");
                }
            })
            .show();
    }

    private EditText numberField(double value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER |
            InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        field.setText(Double.toString(value));
        return field;
    }

    private double boundedPercent(EditText field, boolean includeZero) {
        double value = Double.parseDouble(field.getText().toString().trim());
        return Math.max(includeZero ? 0 : 0, Math.min(100, value));
    }

    private void addNumberControl(String field, String label, int min, int max, int fallback) {
        content.addView(text(label, 14));
        SeekBar control = new SeekBar(this);
        control.setMax(max - min);
        double stored = ThemeDocument.decimal(editedLook, field, fallback);
        int progress;
        if ("backgroundAnimationSpeed".equals(field)) {
            progress = Math.max(0, Math.min(max - min, (int) Math.round((stored - 0.25) / 0.25)));
        } else {
            progress = (int) stored - min;
        }
        control.setProgress(Math.max(0, Math.min(max - min, progress)));
        control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean changed;
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                if (!changed) {
                    pushHistory();
                    changed = true;
                }
                if ("backgroundAnimationSpeed".equals(field)) {
                    editedLook.put(field, 0.25 + value * 0.25);
                } else {
                    editedLook.put(field, (long) (min + value));
                }
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (changed) persistTheme();
            }
        });
        content.addView(control);
    }

    private void importIcons(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The selected icon pack could not be opened.");
            showMessage(KeyboardIconPackRuntime.importPack(this, input));
            showEditor();
        } catch (IOException | SecurityException exception) {
            showMessage(exception.getMessage());
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
            int width = getWidth();
            int height = getHeight();
            android.graphics.Bitmap background = KeyboardThemeRuntime.previewBitmap(
                previewTheme,
                "backgroundImageBase64"
            );
            RectF boardBounds = new RectF(0, 0, width, height);
            if (background != null && !background.isRecycled()) {
                canvas.drawBitmap(background, null, boardBounds, paint);
            } else {
                canvas.drawColor(board);
            }
            paint.setColor(board);
            paint.setAlpha(android.graphics.Color.alpha(board));
            canvas.drawRect(boardBounds, paint);
            paint.setAlpha(255);
            int key = themeColor("keyBackground");
            int textColor = themeColor("keyText");
            android.graphics.Bitmap texture = KeyboardThemeRuntime.previewBitmap(
                previewTheme,
                "keyTextureBase64"
            );
            float radius = Math.max(0, Math.min(height / 6f, (float) ThemeDocument.number(previewTheme, "keyCornerRadiusDp", 6) * getResources().getDisplayMetrics().density));
            float left = dp(12);
            float top = height * 0.28f;
            float keyWidth = (width - dp(36)) / 5f;
            float keyHeight = height * 0.32f;
            for (int i = 0; i < 5; i++) {
                float x = left + i * (keyWidth + dp(3));
                RectF keyBounds = new RectF(x, top, x + keyWidth, top + keyHeight);
                paint.setColor(i == 4 ? themeColor("enterKeyBackground") : key);
                canvas.drawRoundRect(keyBounds, radius, radius, paint);
                drawPreviewTexture(canvas, keyBounds, texture);
                drawPreviewIcon(canvas, "key.letter_" + (char) ('a' + i), String.valueOf((char) ('A' + i)), keyBounds, textColor);
            }
            List<String> importedSlots = KeyboardIconPackRuntime.activeIconSlots(
                KeyboardThemeStudioActivity.this
            );
            String[] modifierSlots = importedSlots.isEmpty()
                ? new String[] { "key.shift", "key.backspace", "key.emoji", "key.enter" }
                : importedSlots.subList(0, Math.min(4, importedSlots.size())).toArray(new String[0]);
            String[] modifierLabels = importedSlots.isEmpty()
                ? new String[] { "⇧", "⌫", "☺", "↵" }
                : new String[modifierSlots.length];
            if (!importedSlots.isEmpty()) {
                for (int index = 0; index < modifierSlots.length; index++) {
                    String slot = modifierSlots[index];
                    modifierLabels[index] = slot.substring(slot.lastIndexOf('.') + 1);
                }
            }
            float rowTop = top + keyHeight + dp(6);
            float rowHeight = Math.max(dp(22), height - rowTop - dp(10));
            float modifierWidth = (width - 2 * left - dp(12)) / modifierSlots.length;
            for (int index = 0; index < modifierSlots.length; index++) {
                float x = left + index * (modifierWidth + dp(4));
                RectF bounds = new RectF(x, rowTop, x + modifierWidth, rowTop + rowHeight);
                paint.setColor(index == modifierSlots.length - 1
                    ? themeColor("enterKeyBackground")
                    : themeColor("modifierKeyBackground"));
                canvas.drawRoundRect(bounds, radius, radius, paint);
                drawPreviewTexture(canvas, bounds, texture);
                drawPreviewIcon(canvas, modifierSlots[index], modifierLabels[index], bounds,
                    index == modifierSlots.length - 1 ? themeColor("enterKeyText") : themeColor("modifierKeyText"));
            }
            drawPreviewStickers(canvas, width, height);
        }

        private void drawPreviewStickers(Canvas canvas, int width, int height) {
            for (Object value : ThemeDocument.array(previewTheme.get("stickers"))) {
                Map<String, Object> sticker = ThemeDocument.object(value);
                android.graphics.Bitmap bitmap = KeyboardThemeRuntime.previewBitmap(sticker, "imageBase64");
                if (bitmap == null || bitmap.isRecycled()) continue;
                float dimension = (float) ThemeDocument.decimal(sticker, "size", 0.18) * width;
                float aspect = (float) bitmap.getHeight() / bitmap.getWidth();
                float centerX = (float) ThemeDocument.decimal(sticker, "x", 0.5) * width;
                float centerY = (float) ThemeDocument.decimal(sticker, "y", 0.5) * height;
                RectF destination = new RectF(
                    centerX - dimension / 2f,
                    centerY - dimension * aspect / 2f,
                    centerX + dimension / 2f,
                    centerY + dimension * aspect / 2f
                );
                int save = canvas.save();
                canvas.rotate((float) ThemeDocument.decimal(sticker, "rotation", 0), centerX, centerY);
                paint.setAlpha((int) ThemeDocument.number(sticker, "opacity", 100) * 255 / 100);
                canvas.drawBitmap(bitmap, null, destination, paint);
                paint.setAlpha(255);
                canvas.restoreToCount(save);
            }
        }

        private void drawPreviewTexture(Canvas canvas, RectF bounds, android.graphics.Bitmap texture) {
            if (texture == null || texture.isRecycled()) return;
            paint.setAlpha((int) ThemeDocument.number(previewTheme, "keyTextureOpacity", 100) * 255 / 100);
            canvas.drawBitmap(texture, null, bounds, paint);
            paint.setAlpha(255);
        }

        private void drawPreviewIcon(Canvas canvas, String slot, String fallback, RectF bounds, int fallbackColor) {
            android.graphics.Bitmap bitmap = KeyboardIconPackRuntime.previewIcon(
                KeyboardThemeStudioActivity.this,
                slot
            );
            if (bitmap != null && !bitmap.isRecycled()) {
                RectF imageBounds = new RectF(
                    bounds.left + bounds.width() * 0.2f,
                    bounds.top + bounds.height() * 0.2f,
                    bounds.right - bounds.width() * 0.2f,
                    bounds.bottom - bounds.height() * 0.2f
                );
                paint.setAlpha(255);
                canvas.drawBitmap(bitmap, null, imageBounds, paint);
                return;
            }
            paint.setColor(fallbackColor);
            paint.setTextSize(Math.min(dp(18), bounds.height() * 0.6f));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(fallback, bounds.centerX(), baseline, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }
}
