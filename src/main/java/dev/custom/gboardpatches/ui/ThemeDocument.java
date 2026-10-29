package dev.custom.gboardpatches.ui;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ThemeDocument {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_VALUES = 20_000;

    private ThemeDocument() {}

    static Map<String, Object> read(InputStream input) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        Object value = readValue(reader, 0, new int[] { 0 });
        if (!(value instanceof Map)) throw new IOException("Theme JSON must contain a JSON object.");
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Theme JSON contains trailing data.");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    static Map<String, Object> parse(String json) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(json));
        Object value = readValue(reader, 0, new int[] { 0 });
        if (!(value instanceof Map)) throw new IOException("Theme JSON must contain a JSON object.");
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Theme JSON contains trailing data.");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    static String write(Map<String, Object> theme) throws IOException {
        StringWriter output = new StringWriter();
        JsonWriter writer = new JsonWriter(output);
        writer.setIndent("  ");
        writeValue(writer, theme);
        writer.flush();
        return output.toString();
    }

    static Map<String, Object> copy(Map<String, Object> theme) throws IOException {
        return parse(write(theme));
    }

    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) return new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    static List<Object> array(Object value) {
        if (!(value instanceof List)) return new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) value;
        return result;
    }

    static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    static long number(Map<String, Object> object, String key, long fallback) {
        Object value = object.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                try {
                    return (long) Double.parseDouble(((String) value).trim());
                } catch (NumberFormatException ignoredAgain) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    static int color(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        if (value instanceof Number) return (int) ((Number) value).longValue();
        if (!(value instanceof String)) return fallback;
        String raw = ((String) value).trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        else if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
        if (raw.length() == 3) {
            raw = new String(new char[] {
                raw.charAt(0), raw.charAt(0), raw.charAt(1), raw.charAt(1), raw.charAt(2), raw.charAt(2)
            });
        }
        if (raw.length() == 6) raw = "FF" + raw;
        if (raw.length() != 8) return fallback;
        try {
            return (int) Long.parseLong(raw, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static String colorString(int color) {
        return String.format(java.util.Locale.ROOT, "#%08X", color);
    }

    private static Object readValue(JsonReader reader, int depth, int[] count) throws IOException {
        if (depth > MAX_DEPTH || ++count[0] > MAX_VALUES) {
            throw new IOException("Theme JSON is too complex.");
        }
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT: {
                Map<String, Object> object = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    if (++count[0] > MAX_VALUES) throw new IOException("Theme JSON is too complex.");
                    object.put(reader.nextName(), readValue(reader, depth + 1, count));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY: {
                List<Object> array = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readValue(reader, depth + 1, count));
                }
                reader.endArray();
                return array;
            }
            case STRING:
                return reader.nextString();
            case NUMBER: {
                String raw = reader.nextString();
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException exception) {
                    try {
                        return Double.parseDouble(raw);
                    } catch (NumberFormatException invalidNumber) {
                        throw new IOException("Theme JSON contains an invalid number.");
                    }
                }
            }
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new IOException("Theme JSON contains an unexpected token.");
        }
    }

    private static void writeValue(JsonWriter writer, Object value) throws IOException {
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) continue;
                writer.name((String) entry.getKey());
                writeValue(writer, entry.getValue());
            }
            writer.endObject();
        } else if (value instanceof List) {
            writer.beginArray();
            for (Object item : (List<?>) value) writeValue(writer, item);
            writer.endArray();
        } else if (value instanceof String) {
            writer.value((String) value);
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else if (value instanceof Number) {
            writer.value((Number) value);
        } else {
            throw new IOException("Theme JSON contains an unsupported value.");
        }
    }
}
