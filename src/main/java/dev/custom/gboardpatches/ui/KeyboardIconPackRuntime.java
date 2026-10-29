package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class KeyboardIconPackRuntime {
    public static final String PREF_ENABLED = "pref_key_keyboard_icons_enabled";
    public static final String PREF_IMPORT = "pref_key_keyboard_icon_pack_import";
    private static final String TAG = "KeyboardIconPacks";
    private static final String PACK_FORMAT = "wmkeyboard-icons";
    private static final int MAX_ARCHIVE_BYTES = 24 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ENTRIES = 400;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final int CACHE_KB = 4 * 1024;
    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<String, Bitmap>(CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private KeyboardIconPackRuntime() {}

    public static void initializePreferences(Object preferenceGroup) {
        if (preferenceGroup == null) return;
        List<Object> preferences = new ArrayList<>();
        collectPreferences(preferenceGroup, preferences);
        for (Object preference : preferences) {
            String key = GlideTrailPreferences.getKey(preference);
            if (PREF_ENABLED.equals(key)) {
                installEnabledListener(preference);
            } else if (PREF_IMPORT.equals(key)) {
                GlideTrailPreferences.setPreferenceClickListener(preference, clickedPreference -> {
                    launchImporter(GlideTrailPreferences.getContext(clickedPreference));
                    return true;
                });
            }
        }
    }

    private static void clearImageTint(ImageView image) {
        try {
            Method method = ImageView.class.getMethod(
                "setImageTintList",
                android.content.res.ColorStateList.class
            );
            method.invoke(image, new Object[] { null });
        } catch (NoSuchMethodException ignored) {
            // Image tint lists are unavailable on older Android versions.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Unable to clear Gboard's icon tint.", exception);
        }
    }

    private static void installEnabledListener(final Object preference) {
        GlideTrailPreferences.setPreferenceChangeListener(preference, (changedPreference, value) -> {
            if (!(value instanceof Boolean)) return false;
            GlideTrailPreferences.savePrefBoolean(
                GlideTrailPreferences.getContext(changedPreference),
                PREF_ENABLED,
                (Boolean) value
            );
            return true;
        });
    }

    public static void applyIcon(View softKeyView, Object softKeyDef, int imageViewId) {
        if (softKeyView == null || softKeyDef == null) return;
        Context context = softKeyView.getContext();
        if (!isEnabled(context)) return;
        String slot = resolveSlot(softKeyView, softKeyDef);
        if (slot == null) return;

        File iconFile = new File(new File(new File(context.getFilesDir(), "morphe-wmicons"), "active"),
            slot + ".icon");
        if (!iconFile.isFile()) return;

        String cacheKey = iconFile.getAbsolutePath();
        Bitmap bitmap = BITMAP_CACHE.get(cacheKey);
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = decodeIcon(iconFile);
            if (bitmap == null) return;
            BITMAP_CACHE.put(cacheKey, bitmap);
        }
        View iconView = softKeyView.findViewById(imageViewId);
        if (iconView instanceof ImageView) {
            ImageView image = (ImageView) iconView;
            image.setColorFilter((android.graphics.ColorFilter) null);
            clearImageTint(image);
            image.setImageBitmap(bitmap);
        }
    }

    public static String importPack(Context context, java.io.InputStream input) throws IOException {
        if (context == null || input == null) throw new IOException("No icon pack was selected.");
        Map<String, byte[]> icons = new HashMap<>();
        byte[][] manifestBytes = new byte[1][];
        long[] totalBytes = new long[1];
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) throw new IOException("The icon pack has too many files.");
                String name = entry.getName();
                boolean manifest = "pack.json".equals(name);
                String slot = null;
                String extension = null;
                if (name != null && name.startsWith("icons/") && name.indexOf('/', 6) < 0) {
                    int dot = name.lastIndexOf('.');
                    if (dot > 6 && dot < name.length() - 1) {
                        slot = name.substring(6, dot);
                        extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                        if (!isSafeSlot(slot)) {
                            slot = null;
                        } else if ("svg".equals(extension)) {
                            throw new IOException("SVG icons are not supported; use PNG, WebP, JPEG, or GIF images.");
                        } else if (!isSupportedImage(extension)) {
                            slot = null;
                        }
                    }
                }

                int entryLimit = manifest ? 64 * 1024 : MAX_ENTRY_BYTES;
                byte[] data = readEntry(zip, entryLimit, totalBytes);
                zip.closeEntry();
                if (manifest) {
                    manifestBytes[0] = data;
                } else if (slot != null && !icons.containsKey(slot)) {
                    icons.put(slot, data);
                }
            }
        }

        if (manifestBytes[0] == null) throw new IOException("The ZIP does not contain pack.json.");
        PackManifest manifest = parseManifest(new String(manifestBytes[0], "UTF-8"));
        if (!PACK_FORMAT.equals(manifest.format)) {
            throw new IOException("This is not a WMKeyboard icon pack.");
        }
        if (manifest.version != 1) {
            throw new IOException("This WMKeyboard icon-pack version is not supported.");
        }
        if (icons.isEmpty()) throw new IOException("No supported raster icons were found in the pack.");

        for (Map.Entry<String, byte[]> icon : icons.entrySet()) {
            validateImage(icon.getValue());
        }

        File parent = new File(context.getFilesDir(), "morphe-wmicons");
        File staging = new File(parent, "staging");
        File active = new File(parent, "active");
        File backup = new File(parent, "backup");
        deleteTree(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new IOException("Gboard could not create its icon-pack storage directory.");
        }
        for (Map.Entry<String, byte[]> icon : icons.entrySet()) {
            File output = new File(staging, icon.getKey() + ".icon");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                stream.write(icon.getValue());
            }
        }

        deleteTree(backup);
        boolean movedOldPack = active.exists();
        if (movedOldPack && !active.renameTo(backup)) {
            deleteTree(staging);
            throw new IOException("Gboard could not replace the previous icon pack.");
        }
        if (!staging.renameTo(active)) {
            if (movedOldPack) backup.renameTo(active);
            deleteTree(staging);
            throw new IOException("Gboard could not activate the imported icon pack.");
        }
        deleteTree(backup);
        BITMAP_CACHE.evictAll();
        GlideTrailPreferences.savePrefBoolean(context, PREF_ENABLED, true);

        String packName = manifest.name == null || manifest.name.isEmpty() ? "WMKeyboard" : manifest.name;
        return "Imported " + packName + " (" + icons.size() + " icons).";
    }

    private static PackManifest parseManifest(String json) throws IOException {
        JsonCursor cursor = new JsonCursor(json);
        PackManifest manifest = cursor.readManifest();
        if (!cursor.isAtEnd()) throw new IOException("pack.json contains trailing data.");
        return manifest;
    }

    private static final class PackManifest {
        String format;
        String name;
        int version = -1;
    }

    private static final class JsonCursor {
        private final String input;
        private int index;

        JsonCursor(String input) {
            this.input = input;
        }

        PackManifest readManifest() throws IOException {
            PackManifest manifest = new PackManifest();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (consume('}')) return manifest;
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = readValue();
                if ("format".equals(key) && value instanceof String) {
                    manifest.format = (String) value;
                } else if ("name".equals(key) && value instanceof String) {
                    manifest.name = (String) value;
                } else if ("version".equals(key) && value instanceof Long) {
                    long version = (Long) value;
                    if (version >= Integer.MIN_VALUE && version <= Integer.MAX_VALUE) {
                        manifest.version = (int) version;
                    }
                }
                skipWhitespace();
                if (consume('}')) return manifest;
                expect(',');
            }
        }

        boolean isAtEnd() {
            skipWhitespace();
            return index == input.length();
        }

        private Object readValue() throws IOException {
            skipWhitespace();
            if (index >= input.length()) throw malformed();
            char current = input.charAt(index);
            if (current == '"') return readString();
            if (current == '{') {
                skipObject();
                return null;
            }
            if (current == '[') {
                skipArray();
                return null;
            }
            if (current == 't') {
                expectLiteral("true");
                return Boolean.TRUE;
            }
            if (current == 'f') {
                expectLiteral("false");
                return Boolean.FALSE;
            }
            if (current == 'n') {
                expectLiteral("null");
                return null;
            }
            return readNumber();
        }

        private void skipObject() throws IOException {
            expect('{');
            skipWhitespace();
            if (consume('}')) return;
            while (true) {
                skipWhitespace();
                readString();
                skipWhitespace();
                expect(':');
                readValue();
                skipWhitespace();
                if (consume('}')) return;
                expect(',');
            }
        }

        private void skipArray() throws IOException {
            expect('[');
            skipWhitespace();
            if (consume(']')) return;
            while (true) {
                readValue();
                skipWhitespace();
                if (consume(']')) return;
                expect(',');
            }
        }

        private String readString() throws IOException {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char value = input.charAt(index++);
                if (value == '"') return result.toString();
                if (value < 0x20) throw malformed();
                if (value != '\\') {
                    result.append(value);
                    continue;
                }
                if (index >= input.length()) throw malformed();
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (index + 4 > input.length()) throw malformed();
                        try {
                            result.append((char) Integer.parseInt(input.substring(index, index + 4), 16));
                        } catch (NumberFormatException exception) {
                            throw malformed();
                        }
                        index += 4;
                        break;
                    default: throw malformed();
                }
            }
            throw malformed();
        }

        private Long readNumber() throws IOException {
            int start = index;
            consume('-');
            if (index >= input.length()) throw malformed();
            if (input.charAt(index) == '0') {
                index++;
                if (index < input.length() && isAsciiDigit(input.charAt(index))) throw malformed();
            } else {
                if (index >= input.length() || input.charAt(index) < '1' || input.charAt(index) > '9') {
                    throw malformed();
                }
                while (index < input.length() && isAsciiDigit(input.charAt(index))) index++;
            }
            boolean integer = true;
            if (consume('.')) {
                integer = false;
                int fractionStart = index;
                while (index < input.length() && isAsciiDigit(input.charAt(index))) index++;
                if (fractionStart == index) throw malformed();
            }
            if (consume('e') || consume('E')) {
                integer = false;
                if (!consume('+')) consume('-');
                int exponentStart = index;
                while (index < input.length() && isAsciiDigit(input.charAt(index))) index++;
                if (exponentStart == index) throw malformed();
            }
            if (!integer) return null;
            try {
                return Long.parseLong(input.substring(start, index));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private void expectLiteral(String value) throws IOException {
            if (!input.regionMatches(index, value, 0, value.length())) throw malformed();
            index += value.length();
        }

        private void skipWhitespace() {
            while (index < input.length()) {
                char value = input.charAt(index);
                if (value != ' ' && value != '\t' && value != '\r' && value != '\n') return;
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) throws IOException {
            if (!consume(expected)) throw malformed();
        }

        private IOException malformed() {
            return new IOException("pack.json is not valid JSON.");
        }

        private boolean isAsciiDigit(char value) {
            return value >= '0' && value <= '9';
        }
    }

    private static void collectPreferences(Object preference, List<Object> output) {
        if (preference == null) return;
        output.add(preference);
        Method countMethod = findMethod(preference.getClass(), "getPreferenceCount");
        if (countMethod == null) countMethod = findMethod(preference.getClass(), "k");
        Method childMethod = findMethod(preference.getClass(), "getPreference", int.class);
        if (childMethod == null) childMethod = findMethod(preference.getClass(), "o", int.class);
        if (countMethod == null || childMethod == null) return;
        try {
            int count = ((Number) countMethod.invoke(preference)).intValue();
            for (int index = 0; index < count; index++) {
                collectPreferences(childMethod.invoke(preference, index), output);
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Log.w(TAG, "Unable to inspect a Gboard preference group.", exception);
        }
    }

    private static void launchImporter(Context context) {
        if (context == null) {
            Log.e(TAG, "Unable to open the icon-pack picker: preference context is unavailable.");
            return;
        }
        try {
            Intent intent = new Intent(context, KeyboardIconPackImportActivity.class);
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to open the icon-pack file picker.", exception);
            Toast.makeText(context, "Unable to open the icon-pack file picker.", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isEnabled(Context context) {
        try {
            SharedPreferences preferences = GlideTrailPreferences.getPrefs(context);
            return preferences != null && preferences.getBoolean(PREF_ENABLED, false);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read the keyboard-icon setting.", exception);
            return false;
        }
    }

    private static String resolveSlot(View view, Object softKeyDef) {
        List<Integer> keyCodes = findKeyCodes(softKeyDef);
        String text = ((view.getContentDescription() == null) ? "" : view.getContentDescription().toString())
            + " " + String.valueOf(softKeyDef);
        String lower = text.toLowerCase(Locale.ROOT);
        for (Integer keyCode : keyCodes) {
            String slot = keyCodeSlot(keyCode, lower);
            if (slot != null) return slot;
        }

        if (containsAny(lower, "clipboard search", "search clipboard")) return "tool.clipboard_search";
        if (containsAny(lower, "paste")) return "tool.paste";
        if (containsAny(lower, "clipboard")) return "tool.clipboard";
        if (containsAny(lower, "text editing", "text edit")) return "tool.text_edit";
        if (containsAny(lower, "settings", "keyboard settings")) return "tool.settings";
        if (containsAny(lower, "undo")) return "tool.undo";
        if (containsAny(lower, "redo")) return "tool.redo";
        if (containsAny(lower, "voice typing", "voice input", "microphone")) return "tool.voice";
        if (containsAny(lower, "grammar")) return "tool.grammar";
        if (containsAny(lower, "hide keyboard", "dismiss keyboard")) return "tool.hide_keyboard";
        if (containsAny(lower, "send file", "send files")) return "tool.send_files";
        if (containsAny(lower, "translate", "translation")) return "tool.translate";
        if (containsAny(lower, "theme")) return "tool.themes";
        if (containsAny(lower, "one-handed", "one handed")) return "tool.one_handed";
        if (containsAny(lower, "incognito")) return "tool.incognito";
        if (containsAny(lower, "floating keyboard", "float keyboard")) return "tool.floating";
        if (containsAny(lower, "gif")) return "tool.gif";
        if (containsAny(lower, "emoji")) return keyCodes.isEmpty() ? "tool.emoji" : "key.emoji";
        if (containsAny(lower, "web search", "search the web")) return "tool.web_search";
        if (containsAny(lower, "handwriting")) return "tool.handwriting";
        if (containsAny(lower, "dictionary")) return "tool.dictionary";
        if (containsAny(lower, "copy")) return "tool.copy";
        if (containsAny(lower, "cut")) return "tool.cut";
        if (containsAny(lower, "toolbar")) return "chrome.toolbox";
        if (containsAny(lower, "hide keys")) return "chrome.hide_keys";
        if (containsAny(lower, "show keys")) return "chrome.show_keys";
        if (containsAny(lower, "expand suggestions", "more suggestions")) return "chrome.suggestions_expand";
        if (containsAny(lower, "close search")) return "chrome.search_close";
        if (containsAny(lower, "shift lock", "caps lock")) return "key.shift_lock";
        if (containsAny(lower, "shift on", "shift enabled")) return "key.shift_on";
        if (containsAny(lower, "shift")) return "key.shift";
        if (containsAny(lower, "backspace", "delete previous")) return "key.backspace";
        if (containsAny(lower, "forward delete", "delete next")) return "key.forward_delete";
        if (containsAny(lower, "tab")) return "key.tab";
        if (containsAny(lower, "enter search", "search key")) return "key.enter_search";
        if (containsAny(lower, "enter send", "send key")) return "key.enter_send";
        if (containsAny(lower, "enter done", "done key")) return "key.enter_done";
        if (containsAny(lower, "enter next", "next key")) return "key.enter_next";
        if (containsAny(lower, "enter previous", "previous key")) return "key.enter_previous";
        if (containsAny(lower, "enter go", "go key")) return "key.enter_go";
        if (containsAny(lower, "enter", "return")) return "key.enter";
        if (containsAny(lower, "input method", "change keyboard")) return "key.input_method_picker";
        if (containsAny(lower, "globe", "language switch")) return "key.globe";
        if (containsAny(lower, "search")) return "key.enter_search";
        return null;
    }

    private static String keyCodeSlot(int keyCode, String description) {
        switch (keyCode) {
            case 19: return "key.arrow_up";
            case 20: return "key.arrow_down";
            case 21: return "key.arrow_left";
            case 22: return "key.arrow_right";
            case 59:
            case 60:
                if (containsAny(description, "caps lock", "shift lock", "shift_lock")) return "key.shift_lock";
                if (containsAny(description, "shift on", "shift_on")) return "key.shift_on";
                return "key.shift";
            case 61: return "key.tab";
            case 66:
                if (containsAny(description, "search")) return "key.enter_search";
                if (containsAny(description, "send")) return "key.enter_send";
                if (containsAny(description, "done")) return "key.enter_done";
                if (containsAny(description, "next")) return "key.enter_next";
                if (containsAny(description, "previous")) return "key.enter_previous";
                if (containsAny(description, "go")) return "key.enter_go";
                return "key.enter";
            case 67: return "key.backspace";
            case 112: return "key.forward_delete";
            case 115: return "key.caps_lock";
            case 204:
                if (containsAny(description, "input method", "change keyboard")) {
                    return "key.input_method_picker";
                }
                return "key.globe";
            default: return null;
        }
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private static List<Integer> findKeyCodes(Object softKeyDef) {
        List<Integer> result = new ArrayList<>();
        Object actionDefs = readField(softKeyDef, "h");
        if (!(actionDefs instanceof Iterable)) return result;
        for (Object actionDef : (Iterable<?>) actionDefs) {
            Object keyDatas = readField(actionDef, "f");
            if (!(keyDatas instanceof Iterable)) continue;
            for (Object keyData : (Iterable<?>) keyDatas) {
                Object value = readField(keyData, "c");
                if (value instanceof Number) result.add(((Number) value).intValue());
            }
        }
        return result;
    }

    private static Object readField(Object instance, String name) {
        if (instance == null) return null;
        Field field = findField(instance.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException | RuntimeException exception) {
            Log.w(TAG, "Unable to read Gboard key metadata field " + name + ".", exception);
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException exception) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Bitmap decodeIcon(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) Log.w(TAG, "Unable to decode imported icon " + file.getName() + ".");
        return bitmap;
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width / sample, height / sample) > 256) sample *= 2;
        return sample;
    }

    private static byte[] readEntry(ZipInputStream zip, int entryLimit, long[] totalBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            totalBytes[0] += read;
            if (totalBytes[0] > MAX_ARCHIVE_BYTES) throw new IOException("The icon pack exceeds the 24 MiB size limit.");
            if (output.size() + read > entryLimit) throw new IOException("An icon-pack file is too large.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isSafeSlot(String slot) {
        return slot != null &&
            slot.matches("[a-z0-9][a-z0-9_.@-]{0,80}") &&
            !slot.contains("..");
    }

    private static boolean isSupportedImage(String extension) {
        return "png".equals(extension) || "webp".equals(extension) || "jpg".equals(extension) ||
            "jpeg".equals(extension) || "gif".equals(extension);
    }

    private static void validateImage(byte[] data) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0 ||
            bounds.outWidth > MAX_IMAGE_DIMENSION ||
            bounds.outHeight > MAX_IMAGE_DIMENSION
        ) {
            throw new IOException("An icon is invalid or exceeds 4096 × 4096 pixels.");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) throw new IOException("An icon could not be decoded.");
        bitmap.recycle();
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Unable to clean previous icon-pack data.");
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Unable to clean previous icon-pack data.");
    }
}
