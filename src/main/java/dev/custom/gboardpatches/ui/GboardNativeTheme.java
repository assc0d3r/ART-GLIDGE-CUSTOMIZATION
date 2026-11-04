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
    private static final String VARIABLES_SHEET = "style_sheet_variables.css";
    private static final String STYLE_SHEET = "style_sheet_md2.css";
    private static final String OVERRIDE_MARKER = "/* Morphe Theme Studio overrides */";

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

    static void writeThemeArchive(File archive, Map<String, Object> theme, String id) throws IOException {
        writeTheme(archive, theme, id);
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
        Map<String, Object> metadata = new LinkedHashMap<>(
            ThemeDocument.object(theme.get("nativeThemeMetadata"))
        );
        metadata.put("format_version", 3);
        metadata.put("id", "morphe_theme_" + id);
        metadata.put("name", ThemeDocument.string(theme, "name", "Gboard Theme"));
        metadata.put("is_light_theme", !Boolean.TRUE.equals(theme.get("dark")));
        if (!metadata.containsKey("is_redesign_theme")) {
            metadata.put("is_redesign_theme", Boolean.TRUE);
        }
        metadata.put("prefer_key_border", Boolean.TRUE.equals(theme.get("prefer_key_border")));
        Map<String, String> styleSheets = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry :
            ThemeDocument.object(theme.get("nativeThemeStyleSheets")).entrySet()) {
            if (entry.getKey().matches("[A-Za-z0-9_.-]{1,128}\\.css") &&
                entry.getValue() instanceof String) {
                styleSheets.put(entry.getKey(), (String) entry.getValue());
            }
        }
        if (styleSheets.isEmpty()) {
            styleSheets.put(VARIABLES_SHEET, variables(theme));
            styleSheets.put(STYLE_SHEET, stylesheet(theme));
            metadata.put("style_sheets", new ArrayList<Object>(styleSheets.keySet()));
        } else {
            List<Object> metadataSheets = validStyleSheets(metadata.get("style_sheets"));
            if (!metadataSheets.contains(VARIABLES_SHEET)) {
                metadataSheets.add(0, VARIABLES_SHEET);
                metadata.put("style_sheets", metadataSheets);
            }
            if (!metadataSheets.contains(STYLE_SHEET)) {
                metadataSheets.add(STYLE_SHEET);
                metadata.put("style_sheets", metadataSheets);
            }
            if (!styleSheets.containsKey(VARIABLES_SHEET)) styleSheets.put(VARIABLES_SHEET, "");
            if (!styleSheets.containsKey(STYLE_SHEET)) styleSheets.put(STYLE_SHEET, "");
        }
        Map<String, String> colors = colorVariables(theme);
        styleSheets.put(VARIABLES_SHEET, updateVariables(
            styleSheets.get(VARIABLES_SHEET),
            colors
        ));
        styleSheets.put(STYLE_SHEET, appendOverrides(
            styleSheets.get(STYLE_SHEET),
            theme
        ));

        String metadataJson = ThemeDocument.write(metadata);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            addEntry(output, "metadata.json", metadataJson);
            for (Map.Entry<String, String> entry : styleSheets.entrySet()) {
                addEntry(output, entry.getKey(), entry.getValue());
            }
        } catch (IOException exception) {
            archive.delete();
            throw new IOException("Gboard could not generate the native theme archive.", exception);
        }
    }

    private static String variables(Map<String, Object> theme) {
        return updateVariables("", colorVariables(theme));
    }

    private static String stylesheet(Map<String, Object> theme) {
        return "@def color_header @web_color_bg;\n"
            + "@def color_base @web_color_bg;\n"
            + "@def color_icon_action @web_color_label;\n"
            + "@def color_state_action_pressed @web_color_accent_pressed;\n"
            + "@def color_state_action @web_color_accent;\n"
            + "@def color_label_function_key @web_color_label;\n"
            + "@def color_label_space_key @morphe_color_spacebar_text;\n"
            + "@def color_state_key_pressed @web_color_key_bg_pressed;\n"
            + "@def color_state_key @web_color_key_bg;\n"
            + "@def color_state_key_dark_pressed @web_color_secondary_key_bg_pressed;\n"
            + "@def color_state_key_dark @web_color_secondary_key_bg;\n"
            + "@def color_state_space_bar_pressed @web_color_secondary_key_bg_pressed;\n"
            + "@def color_state_space_bar @web_color_secondary_key_bg;\n"
            + "@def color_popup_background @web_color_secondary_key_bg;\n"
            + "@def color_label @web_color_label;\n"
            + "@def color_icon @web_color_label;\n"
            + "@def default_bordered_key_color @web_color_key_bg;\n"
            + "@def default_action_key_background_color @web_color_accent;\n"
            + "@def default_action_key_background_color_pressed @web_color_accent_pressed;\n"
            + ".keyboard-body-area, .keyboard-base-area, .keyboard-header-area { "
            + "background_color: @color_base; }\n"
            + ".keytop { background_color: @color_state_key; }\n"
            + ".keytop.dark { background_color: @morphe_color_modifier; }\n"
            + ".keytop.for-space-bar, .space_bar { background_color: @morphe_color_spacebar; }\n"
            + ".keytop.for-action-key { background_color: @morphe_color_enter; }\n"
            + ".keytop:pressed { background_color: @web_color_key_bg_pressed; }\n"
            + ".keytop.dark:pressed { background_color: @web_color_secondary_key_bg_pressed; }\n"
            + ".keytop.for-space-bar:pressed, .space_bar:pressed { "
            + "background_color: @morphe_color_spacebar_pressed; }\n"
            + ".keytop.for-action-key:pressed { background_color: @web_color_accent_pressed; }\n"
            + ".label, .icon { color: @web_color_label; }\n"
            + ".label.for-space-key { color: @morphe_color_spacebar_text; }\n";
    }

    private static List<Object> validStyleSheets(Object value) {
        List<Object> result = new ArrayList<>();
        for (Object name : ThemeDocument.array(value)) {
            if (name instanceof String &&
                ((String) name).matches("[A-Za-z0-9_.-]{1,128}\\.css") &&
                !result.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private static Map<String, String> colorVariables(Map<String, Object> theme) {
        int key = ThemeDocument.color(theme, "keyBackground", 0xFF303338);
        int modifier = ThemeDocument.color(theme, "modifierKeyBackground", key);
        int spacebar = ThemeDocument.color(theme, "spacebarBackground", key);
        int pressed = ThemeDocument.color(
            theme,
            "pressedKeyBackground",
            ThemeDocument.color(theme, "accent", key)
        );
        int text = ThemeDocument.color(theme, "keyText", 0xFFE9E9EE);
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("web_color_bg", cssColor(ThemeDocument.color(theme, "boardBackground", 0xFF17181C)));
        colors.put("web_color_label", cssColor(text));
        colors.put("web_color_accent", cssColor(
            ThemeDocument.color(theme, "accent", 0xFF8AB4F8)
        ));
        colors.put("web_color_accent_pressed", cssColor(pressed));
        colors.put("web_color_tertiary_key_bg", cssColor(modifier));
        colors.put("web_color_tertiary_key_bg_pressed", cssColor(pressed));
        colors.put("web_color_key_bg", cssColor(key));
        colors.put("web_color_key_bg_pressed", cssColor(pressed));
        colors.put("web_color_secondary_key_bg", cssColor(modifier));
        colors.put("web_color_secondary_key_bg_pressed", cssColor(pressed));
        colors.put("morphe_color_modifier", cssColor(modifier));
        colors.put("morphe_color_spacebar", cssColor(spacebar));
        colors.put("morphe_color_spacebar_pressed", cssColor(pressed));
        colors.put("morphe_color_spacebar_text", cssColor(
            ThemeDocument.color(theme, "spacebarText", text)
        ));
        colors.put("morphe_color_enter", cssColor(
            ThemeDocument.color(theme, "enterKeyBackground", 0xFF4C8DF6)
        ));
        return colors;
    }

    private static String updateVariables(String stylesheet, Map<String, String> colors) {
        String result = stylesheet == null ? "" : stylesheet;
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?m)(^\\s*@def\\s+" + java.util.regex.Pattern.quote(entry.getKey()) +
                    "\\s+)(#[0-9A-Fa-f]{6,8})(\\s*;)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(java.util.regex.Matcher.quoteReplacement(
                    matcher.group(1) + entry.getValue() + matcher.group(3)
                ));
            } else {
                missing.add("@def " + entry.getKey() + " " + entry.getValue() + ";");
            }
        }
        if (!missing.isEmpty()) {
            if (!result.isEmpty() && !result.endsWith("\n")) result += "\n";
            StringBuilder additions = new StringBuilder();
            for (String definition : missing) additions.append(definition).append('\n');
            result += additions;
        }
        return result;
    }

    private static String appendOverrides(String stylesheet, Map<String, Object> theme) {
        String source = stylesheet == null ? "" : stylesheet;
        int oldOverrides = source.indexOf(OVERRIDE_MARKER);
        if (oldOverrides >= 0) source = source.substring(0, oldOverrides).trim();
        int radius = (int) ThemeDocument.number(theme, "keyCornerRadiusDp", 6);
        String shape = ThemeDocument.string(theme, "keyShape", "ROUNDED");
        if ("SHARP".equalsIgnoreCase(shape)) radius = 0;
        if ("PILL".equalsIgnoreCase(shape) || "CIRCLE".equalsIgnoreCase(shape)) radius = 999;
        String cornerRadius = Integer.toString(Math.max(0, Math.min(999, radius)));
        return source + (source.isEmpty() ? "" : "\n\n") + OVERRIDE_MARKER + "\n"
            + ".keytop { background_shape: rectangle; background_corner_radius: "
            + cornerRadius + "; }\n"
            + ".keytop.dark { background_color: @morphe_color_modifier; }\n"
            + ".keytop.for-space-bar, .space_bar { background_color: @morphe_color_spacebar; "
            + "background_shape: rectangle; background_corner_radius: "
            + cornerRadius + "; }\n"
            + ".keytop.for-space-bar:pressed, .space_bar:pressed { "
            + "background_color: @morphe_color_spacebar_pressed; }\n"
            + ".keytop.for-action-key { background_color: @morphe_color_enter; }\n"
            + ".keytop:pressed { background_color: @web_color_key_bg_pressed; }\n"
            + ".keytop.dark:pressed { background_color: @web_color_secondary_key_bg_pressed; }\n"
            + ".label, .icon { color: @web_color_label; }\n"
            + ".label.for-space-key { color: @morphe_color_spacebar_text; }\n";
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
