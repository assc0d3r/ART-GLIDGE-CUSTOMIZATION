package dev.custom.gboardpatches.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class GboardNativeTheme {
    private static final String TAG = "GboardThemeStudio";
    private static final String THEME_PREFERENCE = "additional_keyboard_theme";
    private static final String BORDER_PREFERENCE = "enable_key_border";
    private static final String STYLE_SHEET = "style_sheet_md2.css";

    private GboardNativeTheme() {}

    static void activate(Context context, String id, Map<String, Object> theme) throws IOException {
        SharedPreferences preferences = GlideTrailPreferences.getPrefs(context);
        if (preferences == null) {
            throw new IOException("Gboard's theme preferences are unavailable.");
        }
        boolean isDefault = "builtin_default".equals(id);
        File target = null;
        File backup = null;
        boolean hadPrevious = false;
        if (!isDefault) {
            File directory = context.getFilesDir();
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
                throw new IOException("Gboard could not access its native theme directory.");
            }
            target = new File(directory, archiveName(id));
            File temporary = new File(directory, target.getName() + ".tmp.zip");
            backup = new File(directory, target.getName() + ".bak");
            writeTheme(temporary, theme, id);
            try {
                validateArchive(context, temporary);
            } catch (IOException exception) {
                temporary.delete();
                throw exception;
            }

            if (backup.exists() && !backup.delete()) {
                temporary.delete();
                throw new IOException("Gboard could not prepare the native theme update.");
            }
            hadPrevious = target.exists();
            if (hadPrevious && !target.renameTo(backup)) {
                temporary.delete();
                throw new IOException("Gboard could not replace the native theme.");
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
                if (hadPrevious && !backup.renameTo(target)) {
                    throw new IOException("Gboard could not install or restore the native theme.");
                }
                throw new IOException("Gboard could not install the native theme.");
            }
        }

        SharedPreferences.Editor editor = preferences.edit()
            .putString(ThemeStore.PREF_ACTIVE_THEME, id);
        if (isDefault) {
            editor.remove(THEME_PREFERENCE).remove(BORDER_PREFERENCE);
        } else {
            editor.putString(THEME_PREFERENCE, "files:" + target.getName())
                .putBoolean(BORDER_PREFERENCE, Boolean.TRUE.equals(theme.get("prefer_key_border")));
        }
        if (!editor.commit()) {
            if (target != null) {
                target.delete();
                if (hadPrevious && backup != null) backup.renameTo(target);
            }
            throw new IOException("Gboard could not activate the selected theme.");
        }
        if (backup != null && backup.exists() && !backup.delete()) {
            Log.w(TAG, "Old native theme backup could not be removed.");
        }
    }

    static void delete(Context context, String id) {
        File archive = new File(context.getFilesDir(), archiveName(id));
        if (archive.exists() && !archive.delete()) Log.w(TAG, "An unused native theme archive could not be removed.");
    }

    private static void validateArchive(Context context, File archive) throws IOException {
        try {
            Class<?> validator = Class.forName("rnw", false, context.getClassLoader());
            Method method = validator.getDeclaredMethod("h", File.class);
            method.setAccessible(true);
            Object receiver = null;
            if (!Modifier.isStatic(method.getModifiers())) {
                receiver = singleton(validator);
                if (receiver == null) {
                    Constructor<?> constructor = validator.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    receiver = constructor.newInstance();
                }
            }
            Object result = method.invoke(receiver, archive);
            if (!Boolean.TRUE.equals(result)) {
                throw new IOException("Gboard rejected the generated native theme archive.");
            }
        } catch (IOException exception) {
            throw exception;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            Log.e(TAG, "Gboard's native theme validator is unavailable.", exception);
            throw new IOException(
                "This Gboard version does not expose its native theme validator; native theme activation is unavailable.",
                exception
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            Log.e(TAG, "Gboard's native theme validator failed.", cause);
            throw new IOException("Gboard could not validate the generated native theme.", cause);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.e(TAG, "Gboard's native theme validator is unavailable.", exception);
            throw new IOException(
                "Gboard's native theme validator could not be called; native theme activation is unavailable.",
                exception
            );
        }
    }

    private static Object singleton(Class<?> type) throws ReflectiveOperationException {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value != null) return value;
        }
        return null;
    }

    private static void writeTheme(File archive, Map<String, Object> theme, String id) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("format_version", 3);
        metadata.put("id", "morphe_theme_" + id);
        metadata.put("name", ThemeDocument.string(theme, "name", "Gboard Theme"));
        metadata.put("is_light_theme", !Boolean.TRUE.equals(theme.get("dark")));
        metadata.put("is_redesign_theme", Boolean.TRUE);
        metadata.put("prefer_key_border", Boolean.FALSE);
        List<Object> styleSheets = new ArrayList<>();
        styleSheets.add(STYLE_SHEET);
        metadata.put("style_sheets", styleSheets);

        String metadataJson = ThemeDocument.write(metadata);
        String variablesCss = variables(theme);
        String themeCss = stylesheet(theme);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            addEntry(output, "metadata.json", metadataJson);
            addEntry(output, "style_sheet_variables.css", variablesCss);
            addEntry(output, "style_sheet_md2.css", themeCss);
        } catch (IOException exception) {
            archive.delete();
            throw new IOException("Gboard could not generate the native theme archive.", exception);
        }
    }

    private static String variables(Map<String, Object> theme) {
        int board = ThemeDocument.color(theme, "boardBackground", 0xFF17181C);
        int key = ThemeDocument.color(theme, "keyBackground", 0xFF303338);
        int pressed = ThemeDocument.color(
            theme,
            "pressedKeyBackground",
            ThemeDocument.color(theme, "accent", key)
        );
        int text = ThemeDocument.color(theme, "keyText", 0xFFE9E9EE);
        int enter = ThemeDocument.color(theme, "enterKeyBackground", 0xFF4C8DF6);
        int modifier = ThemeDocument.color(theme, "modifierKeyBackground", key);
        return "@def color_set_a1 " + cssColor(board) + ";\n"
            + "@def color_set_a2 " + cssColor(key) + ";\n"
            + "@def color_set_a3 " + cssColor(pressed) + ";\n"
            + "@def color_set_a4 " + cssColor(text) + ";\n"
            + "@def color_set_a5 " + cssColor(enter) + ";\n"
            + "@def color_set_a6 " + cssColor(modifier) + ";\n";
    }

    private static String stylesheet(Map<String, Object> theme) {
        String shape = ThemeDocument.string(theme, "keyShape", "ROUNDED");
        int radius = (int) ThemeDocument.number(theme, "keyCornerRadiusDp", 6);
        if ("SHARP".equalsIgnoreCase(shape)) radius = 0;
        if ("PILL".equalsIgnoreCase(shape) || "CIRCLE".equalsIgnoreCase(shape)) radius = 999;
        String cornerRadius = Math.max(0, Math.min(999, radius)) + "dp";
        return ".keyboard-body-area { background_color: @color_set_a1; }\n"
            + ".keyboard-base-area, .keyboard-header-area { background_color: @color_set_a1; }\n"
            + ".keytop, .keytop.dark, .keytop.for-numpad-big-panel { "
            + "background_color: @color_set_a2; background_shape: rectangle; "
            + "background_corner_radius: " + cornerRadius + "; }\n"
            + ".keytop:pressed, .keytop.dark:pressed { background_color: @color_set_a3; }\n"
            + ".keytop.for-action-key, .keytop.for-action-key-holder { "
            + "background_color: @color_set_a5; }\n"
            + ".keytop.for-action-key:pressed, .keytop.for-action-key-holder:pressed { "
            + "background_color: @color_set_a3; }\n"
            + ".label, .icon, .label.for-function-key, .icon.for-function-key, "
            + ".label.for-space-key { color: @color_set_a4; }\n"
            + ".keytop.for-modifier-key { background_color: @color_set_a6; }\n";
    }

    private static String cssColor(int color) {
        return String.format(
            java.util.Locale.ROOT,
            "#%02X%02X%02X%02X",
            (color >>> 16) & 0xFF,
            (color >>> 8) & 0xFF,
            color & 0xFF,
            (color >>> 24) & 0xFF
        );
    }

    private static void addEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static String archiveName(String id) {
        String safeId = id.replaceAll("[^A-Za-z0-9_-]", "_");
        return "morphe_theme_studio_" + safeId + ".zip";
    }
}
