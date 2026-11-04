package dev.custom.gboardpatches.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class GboardThemePackage {
    private static final int MAX_ARCHIVE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRIES = 128;
    private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    private static final Pattern COLOR_DEFINITION = Pattern.compile(
        "(?m)^\\s*@def\\s+([A-Za-z0-9_]+)\\s+(#[0-9A-Fa-f]{6,8})\\s*;"
    );

    private GboardThemePackage() {}

    static Map<String, Object> importPackage(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_ARCHIVE_BYTES) {
            throw new IOException("Theme packages must be between 1 byte and 8 MiB.");
        }
        Map<String, byte[]> entries = readZip(bytes);
        Map<String, byte[]> themeEntries = entries;
        String packName = packName(entries.get("pack.meta"));
        if (!entries.containsKey("metadata.json")) {
            themeEntries = findNestedTheme(entries);
        }

        Map<String, Object> metadata = readMetadata(themeEntries.get("metadata.json"));
        Map<String, String> styleSheets = readStyleSheets(themeEntries, metadata);
        if (styleSheets.isEmpty()) throw new IOException("The package has no supported Gboard stylesheets.");

        Map<String, Object> theme = new LinkedHashMap<>();
        String name = ThemeDocument.string(metadata, "name", packName);
        theme.put("name", name == null || name.trim().isEmpty() ? "Imported Gboard theme" : name);
        theme.put("dark", !Boolean.TRUE.equals(metadata.get("is_light_theme")));
        theme.put("prefer_key_border", Boolean.TRUE.equals(metadata.get("prefer_key_border")));
        theme.put("nativeThemeMetadata", metadata);
        theme.put("nativeThemeStyleSheets", styleSheets);
        readPalette(theme, styleSheets);
        return theme;
    }

    private static Map<String, byte[]> findNestedTheme(Map<String, byte[]> entries) throws IOException {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) continue;
            try {
                Map<String, byte[]> nested = readZip(entry.getValue());
                if (nested.containsKey("metadata.json")) return nested;
            } catch (IOException ignored) {
                // Other ZIP entries in a .pack may be unrelated resources.
            }
        }
        throw new IOException("The package does not contain a native Gboard theme archive.");
    }

    private static Map<String, Object> readMetadata(byte[] bytes) throws IOException {
        if (bytes == null) throw new IOException("The Gboard theme archive is missing metadata.json.");
        Map<String, Object> metadata = ThemeDocument.read(new ByteArrayInputStream(bytes));
        if (ThemeDocument.number(metadata, "format_version", -1) != 3) {
            throw new IOException("This Gboard theme metadata version is not supported.");
        }
        return metadata;
    }

    private static Map<String, String> readStyleSheets(
        Map<String, byte[]> entries,
        Map<String, Object> metadata
    ) throws IOException {
        List<String> names = new ArrayList<>();
        addStyleSheetNames(names, metadata.get("style_sheets"));
        for (Object value : ThemeDocument.array(metadata.get("flavors"))) {
            Map<String, Object> flavor = ThemeDocument.object(value);
            addStyleSheetNames(names, flavor.get("style_sheets"));
        }
        if (names.isEmpty()) throw new IOException("The Gboard theme metadata lists no stylesheets.");

        Map<String, String> styleSheets = new LinkedHashMap<>();
        for (String name : names) {
            byte[] content = entries.get(name);
            if (content == null) throw new IOException("The Gboard theme is missing " + name + ".");
            if (content.length > 512 * 1024) {
                throw new IOException("Gboard stylesheet files must be 512 KiB or smaller.");
            }
            styleSheets.put(name, new String(content, StandardCharsets.UTF_8));
        }
        return styleSheets;
    }

    private static void addStyleSheetNames(List<String> names, Object value) throws IOException {
        if (value == null) return;
        if (!(value instanceof List)) throw new IOException("Gboard stylesheet lists must be arrays.");
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) throw new IOException("Gboard stylesheet names must be strings.");
            String name = (String) item;
            if (!name.matches("[A-Za-z0-9_.-]{1,128}\\.css")) {
                throw new IOException("The Gboard theme contains an unsupported stylesheet name.");
            }
            if (!names.contains(name)) names.add(name);
        }
    }

    private static void readPalette(Map<String, Object> theme, Map<String, String> styleSheets) {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String css : styleSheets.values()) {
            Matcher matcher = COLOR_DEFINITION.matcher(css);
            while (matcher.find()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("color", matcher.group(2));
                colors.put(matcher.group(1), ThemeDocument.color(value, "color", 0));
            }
        }
        copyColor(theme, colors, "boardBackground", "web_color_bg", 0xFF17181C);
        copyColor(theme, colors, "keyText", "web_color_label", 0xFFE9E9EE);
        copyColor(theme, colors, "keyBackground", "web_color_key_bg", 0xFF303338);
        copyColor(theme, colors, "modifierKeyBackground", "web_color_secondary_key_bg", 0xFF222428);
        copyColor(theme, colors, "spacebarBackground", "default_borderless_space_bar_color",
            colors.containsKey("web_color_secondary_key_bg")
                ? colors.get("web_color_secondary_key_bg")
                : 0xFF303338);
        copyColor(theme, colors, "enterKeyBackground", "web_color_accent", 0xFF4C8DF6);
        copyColor(theme, colors, "pressedKeyBackground", "web_color_accent_pressed", 0xFF8AB4F8);
        copyColor(theme, colors, "accent", "web_color_accent", 0xFF8AB4F8);
    }

    private static void copyColor(
        Map<String, Object> theme,
        Map<String, Integer> colors,
        String field,
        String variable,
        int fallback
    ) {
        Integer value = colors.get(variable);
        theme.put(field, (long) (value == null ? fallback : value));
    }

    private static String packName(byte[] bytes) {
        if (bytes == null) return null;
        String content = new String(bytes, StandardCharsets.UTF_8);
        for (String line : content.split("\\r?\\n")) {
            if (line.startsWith("name=")) return line.substring("name=".length()).trim();
        }
        return null;
    }

    private static Map<String, byte[]> readZip(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_ARCHIVE_BYTES) {
            throw new IOException("The theme archive exceeds the 8 MiB size limit.");
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IOException("The theme archive has too many files.");
                String name = entry.getName();
                if (name == null || !isSafeEntryName(name)) {
                    throw new IOException("The theme archive contains an unsafe file path.");
                }
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                if (entries.containsKey(name)) throw new IOException("The theme archive has duplicate filenames.");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    if (output.size() + read > MAX_ENTRY_BYTES ||
                        total + output.size() + read > MAX_ARCHIVE_BYTES) {
                        throw new IOException("The expanded theme archive exceeds the size limit.");
                    }
                    output.write(buffer, 0, read);
                }
                byte[] content = output.toByteArray();
                total += content.length;
                entries.put(name, content);
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IOException("The theme package could not be read: " + exception.getMessage(), exception);
        }
        if (entries.isEmpty()) throw new IOException("The selected file is not a supported ZIP theme package.");
        return entries;
    }

    private static boolean isSafeEntryName(String name) {
        if (name.startsWith("/") || name.startsWith("\\") || name.indexOf('\\') >= 0) return false;
        for (String segment : name.split("/")) {
            if ("..".equals(segment)) return false;
        }
        return name.matches("[A-Za-z0-9_. /-]{1,256}");
    }
}
