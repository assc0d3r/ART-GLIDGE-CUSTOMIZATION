package dev.custom.gboardpatches.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MultilingualSuggestionHelper {
    private static final String TAG = "MorpheMultiSuggest";
    private static final String PREF_ENABLED = "pref_key_multilingual_suggestions_enabled";
    private static final Map<Class<?>, Field> TEXT_FIELDS = new HashMap<>();
    private static boolean warnedAboutCandidateShape;

    private MultilingualSuggestionHelper() {}

    public static List<?> reorderCandidates(Context context, List<?> candidates) {
        if (context == null || candidates == null || candidates.size() < 3) return candidates;

        SharedPreferences preferences = GlideTrailPreferences.getPrefs(context);
        if (preferences == null) return candidates;
        if (!preferences.getBoolean(PREF_ENABLED, false)) return candidates;

        return reorderByScript(candidates);
    }

    static List<?> reorderByScript(List<?> candidates) {
        if (candidates == null || candidates.size() < 3) return candidates;

        Character.UnicodeScript primaryScript = scriptOf(candidates.get(0));
        if (primaryScript == null) return candidates;

        int alternateIndex = -1;
        for (int i = 1; i < candidates.size(); i++) {
            Character.UnicodeScript candidateScript = scriptOf(candidates.get(i));
            if (candidateScript != null && candidateScript != primaryScript) {
                alternateIndex = i;
                break;
            }
        }
        if (alternateIndex <= 1) return candidates;

        List<Object> reordered = new ArrayList<>(candidates);
        Object alternate = reordered.remove(alternateIndex);
        reordered.add(1, alternate);
        return reordered;
    }

    private static Character.UnicodeScript scriptOf(Object candidate) {
        if (candidate == null) return null;
        try {
            Field textField = textFieldFor(candidate.getClass());
            Object value = textField.get(candidate);
            if (!(value instanceof CharSequence)) return null;

            CharSequence text = (CharSequence) value;
            for (int offset = 0; offset < text.length();) {
                int codePoint = Character.codePointAt(text, offset);
                offset += Character.charCount(codePoint);
                if (!Character.isLetter(codePoint)) continue;

                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                if (script != Character.UnicodeScript.COMMON &&
                    script != Character.UnicodeScript.INHERITED) {
                    return script;
                }
            }
        } catch (ReflectiveOperationException | SecurityException exception) {
            warnAboutCandidateShape(exception);
        }
        return null;
    }

    private static Field textFieldFor(Class<?> candidateClass) throws NoSuchFieldException {
        synchronized (TEXT_FIELDS) {
            Field cachedField = TEXT_FIELDS.get(candidateClass);
            if (cachedField != null) return cachedField;

            Field textField = candidateClass.getDeclaredField("a");
            textField.setAccessible(true);
            TEXT_FIELDS.put(candidateClass, textField);
            return textField;
        }
    }

    private static synchronized void warnAboutCandidateShape(Exception exception) {
        if (warnedAboutCandidateShape) return;
        warnedAboutCandidateShape = true;
        Log.w(TAG, "Could not read Gboard suggestion text; leaving candidate order unchanged.", exception);
    }
}
