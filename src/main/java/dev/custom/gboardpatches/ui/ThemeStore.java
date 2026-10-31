package dev.custom.gboardpatches.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ThemeStore {
    static final String PREF_ACTIVE_THEME = "pref_key_gboard_theme_studio_active";
    private static final String TAG = "GboardThemeStudio";
    private static final String DIRECTORY = "morphe-theme-studio";
    private static final String DEFAULT_THEME = "builtin_default";
    private static final String[] PRESET_NAMES = {
        "Ocean", "Snow", "Forest", "Sunset", "Pitch Black", "Lavender"
    };

    private ThemeStore() {}

    static List<Map<String, Object>> list(Context context) {
        File directory = themeDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Unable to create the theme directory.");
            return new ArrayList<>();
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        List<Map<String, Object>> themes = new ArrayList<>();
        if (files == null) return themes;
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            try (FileInputStream input = new FileInputStream(file)) {
                Map<String, Object> theme = ThemeDocument.read(input);
                if (theme.get("id") instanceof String && theme.get("name") instanceof String) {
                    themes.add(theme);
                }
            } catch (IOException exception) {
                Log.w(TAG, "Skipping unreadable theme " + file.getName() + ".", exception);
            }
        }
        return themes;
    }

    static Map<String, Object> load(Context context, String id) throws IOException {
        if (id == null || !isSafeId(id)) throw new IOException("The selected theme could not be found.");
        File file = themeFile(context, id);
        if (!file.isFile()) throw new IOException("The selected theme could not be found.");
        try (FileInputStream input = new FileInputStream(file)) {
            return ThemeDocument.read(input);
        }
    }

    static Map<String, Object> active(Context context) {
        String id = preferences(context).getString(PREF_ACTIVE_THEME, DEFAULT_THEME);
        if (DEFAULT_THEME.equals(id)) return defaultTheme();
        try {
            return load(context, id);
        } catch (IOException exception) {
            Log.w(TAG, "Falling back to the default keyboard theme.", exception);
            try {
                setActive(context, DEFAULT_THEME);
            } catch (IOException activationException) {
                Log.w(TAG, "Unable to reset the native Gboard theme.", activationException);
            }
            return defaultTheme();
        }
    }

    static String activeId(Context context) {
        return preferences(context).getString(PREF_ACTIVE_THEME, DEFAULT_THEME);
    }

    static void setActive(Context context, String id) throws IOException {
        if (id == null || !isSafeId(id) && !DEFAULT_THEME.equals(id)) {
            throw new IOException("The selected theme could not be found.");
        }
        Map<String, Object> theme = DEFAULT_THEME.equals(id) ? defaultTheme() : load(context, id);
        setActive(context, id, theme);
    }

    static void setActive(Context context, String id, Map<String, Object> look) throws IOException {
        if (id == null || !isSafeId(id) && !DEFAULT_THEME.equals(id)) {
            throw new IOException("The selected theme could not be found.");
        }
        GboardNativeTheme.activate(context, id, look);
        KeyboardThemeRuntime.invalidate();
    }

    static void save(Context context, Map<String, Object> theme) throws IOException {
        String id = ThemeDocument.string(theme, "id", null);
        String name = ThemeDocument.string(theme, "name", null);
        if (id == null || !isSafeId(id) || name == null || name.trim().isEmpty()) {
            throw new IOException("A theme must have a name and a valid id.");
        }
        String json = ThemeDocument.write(theme);
        File directory = themeDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Gboard could not create the theme storage directory.");
        }
        File destination = themeFile(context, id);
        File temporary = new File(directory, id + ".json.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        File backup = new File(directory, id + ".json.bak");
        if (backup.exists() && !backup.delete()) {
            temporary.delete();
            throw new IOException("Gboard could not prepare the saved theme.");
        }
        boolean backedUp = destination.exists();
        if (backedUp && !destination.renameTo(backup)) {
            temporary.delete();
            throw new IOException("Gboard could not replace the saved theme.");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            if (backedUp && !backup.renameTo(destination)) {
                throw new IOException("Gboard could not save the theme or restore its previous copy.");
            }
            throw new IOException("Gboard could not save the theme.");
        }
        if (backedUp && !backup.delete()) Log.w(TAG, "Saved theme backup could not be removed.");
        KeyboardThemeRuntime.invalidate();
    }

    static void delete(Context context, String id) throws IOException {
        if (!isSafeId(id)) throw new IOException("The selected theme could not be deleted.");
        File file = themeFile(context, id);
        if (file.exists() && !file.delete()) throw new IOException("Gboard could not delete the theme.");
        if (id.equals(activeId(context))) setActive(context, DEFAULT_THEME);
        GboardNativeTheme.delete(context, id);
    }

    static Map<String, Object> create(Context context, String presetName) {
        Map<String, Object> theme = preset(context, presetName);
        theme.put("id", "custom_" + UUID.randomUUID().toString().replace("-", ""));
        theme.put("name", presetName);
        return theme;
    }

    static Map<String, Object> importTheme(Context context, Map<String, Object> theme) throws IOException {
        if (!theme.containsKey("name") || !(theme.get("name") instanceof String)) {
            throw new IOException("Theme JSON must include a name.");
        }
        validateFamily(theme);
        theme.put("id", "custom_" + UUID.randomUUID().toString().replace("-", ""));
        remintVariants(theme);
        save(context, theme);
        setActive(context, ThemeDocument.string(theme, "id", null));
        return theme;
    }

    static Map<String, Object> duplicate(Context context, Map<String, Object> source) throws IOException {
        Map<String, Object> copy = ThemeDocument.copy(source);
        copy.put("id", "custom_" + UUID.randomUUID().toString().replace("-", ""));
        copy.put("name", ThemeDocument.string(source, "name", "Theme") + " copy");
        remintVariants(copy);
        save(context, copy);
        return copy;
    }

    static Map<String, Object> exportCopy(Map<String, Object> source) throws IOException {
        Map<String, Object> copy = ThemeDocument.copy(source);
        normalizeColorFields(copy);
        return copy;
    }

    static String[] presetNames() {
        return PRESET_NAMES.clone();
    }

    static Map<String, Object> defaultTheme() {
        Map<String, Object> theme = new LinkedHashMap<>();
        theme.put("id", DEFAULT_THEME);
        theme.put("name", "Default (system)");
        theme.put("dark", Boolean.TRUE);
        theme.put("boardBackground", 0xFF17181CL);
        theme.put("keyBackground", 0xFF303338L);
        theme.put("keyText", 0xFFE9E9EEL);
        theme.put("modifierKeyBackground", 0xFF222428L);
        theme.put("modifierKeyText", null);
        theme.put("enterKeyBackground", 0xFF4C8DF6L);
        theme.put("enterKeyText", 0xFF0B1220L);
        theme.put("pressedKeyBackground", null);
        theme.put("accent", 0xFF8AB4F8L);
        theme.put("keyShape", "ROUNDED");
        theme.put("keyCornerRadiusDp", 6L);
        return theme;
    }

    private static Map<String, Object> preset(Context context, String name) {
        boolean dark = (context.getResources().getConfiguration().uiMode & 0x30) != 0x10;
        Map<String, Object> theme = defaultTheme();
        theme.put("dark", dark);
        theme.put("name", name);
        long board;
        long key;
        long text;
        long modifier;
        long enter;
        long accent;
        if ("Snow".equals(name)) {
            board = 0xFFE6E9F0L;
            key = 0xFFFFFFFFL;
            text = 0xFF20232BL;
            modifier = 0xFFD8DDE7L;
            enter = 0xFF2868C7L;
            accent = 0xFF2868C7L;
        } else if ("Forest".equals(name)) {
            board = 0xFF12221CL;
            key = 0xFF243B2FL;
            text = 0xFFE1F1E7L;
            modifier = 0xFF1B3025L;
            enter = 0xFF3F815BL;
            accent = 0xFF8DD9A9L;
        } else if ("Sunset".equals(name)) {
            board = 0xFF281725L;
            key = 0xFF48293AL;
            text = 0xFFFFE9DFL;
            modifier = 0xFF36202FL;
            enter = 0xFFBC4F55L;
            accent = 0xFFFFA06BL;
        } else if ("Pitch Black".equals(name)) {
            board = 0xFF000000L;
            key = 0xFF151515L;
            text = 0xFFF3F3F3L;
            modifier = 0xFF0B0B0BL;
            enter = 0xFF424242L;
            accent = 0xFFB8C7E0L;
        } else if ("Lavender".equals(name)) {
            board = 0xFF211B2DL;
            key = 0xFF372D48L;
            text = 0xFFF1E9FFL;
            modifier = 0xFF2C2339L;
            enter = 0xFF7454A6L;
            accent = 0xFFD0B7FFL;
        } else {
            board = 0xFF151D2BL;
            key = 0xFF263750L;
            text = 0xFFE8F1FFL;
            modifier = 0xFF1D2A3DL;
            enter = 0xFF3477C8L;
            accent = 0xFF82B9FFL;
        }
        theme.put("boardBackground", board);
        theme.put("keyBackground", key);
        theme.put("keyText", text);
        theme.put("modifierKeyBackground", modifier);
        theme.put("modifierKeyText", text);
        theme.put("enterKeyBackground", enter);
        theme.put("enterKeyText", text);
        theme.put("pressedKeyBackground", accent);
        theme.put("accent", accent);
        return theme;
    }

    private static void validateFamily(Map<String, Object> theme) throws IOException {
        Object variants = theme.get("variants");
        if (variants == null) return;
        if (!(variants instanceof List)) throw new IOException("Theme variants must be a JSON array.");
        if (((List<?>) variants).size() > 12) throw new IOException("A theme family can contain at most 13 looks.");
        for (Object item : (List<?>) variants) {
            if (!(item instanceof Map)) throw new IOException("A theme look must be a JSON object.");
            @SuppressWarnings("unchecked")
            Map<String, Object> variant = (Map<String, Object>) item;
            if (!(variant.get("name") instanceof String)) {
                throw new IOException("Every theme look must have a name.");
            }
            variant.remove("variants");
        }
    }

    private static void remintVariants(Map<String, Object> theme) {
        Object variants = theme.get("variants");
        if (!(variants instanceof List)) return;
        for (Object value : (List<?>) variants) {
            if (!(value instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> variant = (Map<String, Object>) value;
            variant.put("id", "custom_" + UUID.randomUUID().toString().replace("-", ""));
            variant.remove("variants");
        }
    }

    private static void normalizeColorFields(Map<String, Object> theme) {
        String[] keys = {
            "boardBackground", "keyBackground", "keyText", "modifierKeyBackground",
            "modifierKeyText", "enterKeyBackground", "enterKeyText", "pressedKeyBackground",
            "accent", "keyBorderColor", "toolbarBackground", "toolbarToolBackground",
            "toolbarToolIcon", "suggestionStripBackground", "suggestionText",
            "popupBackground", "popupText", "popupOutline"
        };
        for (String key : keys) {
            Object value = theme.get(key);
            if (value instanceof Number) {
                theme.put(key, ThemeDocument.colorString((int) ((Number) value).longValue()));
            }
        }
        for (Object value : ThemeDocument.array(theme.get("variants"))) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> variant = (Map<String, Object>) value;
                normalizeColorFields(variant);
            }
        }
        Map<String, Object> gradient = ThemeDocument.object(theme.get("boardGradient"));
        List<Object> stops = ThemeDocument.array(gradient.get("colors"));
        for (int index = 0; index < stops.size(); index++) {
            Object value = stops.get(index);
            if (value instanceof Number) {
                stops.set(index, ThemeDocument.colorString((int) ((Number) value).longValue()));
            }
        }
    }

    private static SharedPreferences preferences(Context context) {
        return GlideTrailPreferences.getPrefs(context);
    }

    private static File themeDirectory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    private static File themeFile(Context context, String id) {
        return new File(themeDirectory(context), id + ".json");
    }

    private static boolean isSafeId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,100}");
    }
}
