package dev.custom.gboardpatches.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.inputmethod.EditorInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-app typing profiles.
 *
 * Gboard is one keyboard for every app, so a package that should not learn from what is typed
 * (a password store, a banking app, a work chat) has no say over it. A profile is a set of
 * restrictions attached to a package name, stored on the device in this app's private
 * preferences, and read every time the keyboard activates for a field:
 *
 * <pre>
 *   rules:  &lt;package&gt;                 every field of that app
 *           &lt;package&gt;#&lt;area&gt;          one kind of field (text, url, email, password, number, other)
 *           &lt;prefix&gt;*                 every package whose name starts with &lt;prefix&gt;
 *   value:  comma separated tokens: "incognito", "no_suggestions",
 *           "toolbar:&lt;id&gt;;&lt;id&gt;", "theme:&lt;theme spec&gt;", "height:&lt;height spec&gt;"
 * </pre>
 *
 * A rule only ever restricts typing behaviour: it can turn learning off and hide the suggestion
 * strip, never turn on something the app did not ask for. The appearance tokens are not a second
 * settings pipeline: they are written through the writers Gboard itself uses — the toolbar order
 * through {@code mog.p(Context, Lnru, List)} (the same call the toolbar editor makes), the theme
 * and the keyboard height through the {@code additional_keyboard_theme} preference, which is also
 * where a height lives ({@code keyboard_height_33_mm} … {@code keyboard_height_52_mm}). The values the keyboard had
 * before the profile took over are restored as soon as another package takes focus. Nothing is
 * logged and nothing leaves the device.
 *
 * An override set through {@link #overrideForSession(boolean, boolean)} replaces the rule for the
 * package that is in focus and is dropped as soon as another package takes focus.
 */
public final class AppProfileRuntime {

    private static final String PREFS_NAME = "gboard_app_profiles";
    private static final String AREA_SEPARATOR = "#";
    private static final String PREFIX_SUFFIX = "*";

    private static final String FLAG_INCOGNITO = "incognito";
    private static final String FLAG_NO_SUGGESTIONS = "no_suggestions";
    private static final String TOKEN_TOOLBAR = "toolbar:";
    private static final String TOKEN_THEME = "theme:";
    private static final String TOKEN_HEIGHT = "height:";

    /**
     * The fields the Per-app profiles section in Gboard's Preferences screen writes.
     * They are ordinary preference keys, so the settings screen needs no extra code; this runtime
     * reads the same keys when the keyboard activates.
     */
    private static final String SETTING_PACKAGE = "pref_key_app_profile_package";
    private static final String SETTING_INCOGNITO = "pref_key_app_profile_incognito";
    private static final String SETTING_NO_SUGGESTIONS = "pref_key_app_profile_no_suggestions";
    private static final String SETTING_TOOLBAR = "pref_key_app_profile_toolbar";
    private static final String SETTING_THEME = "pref_key_app_profile_theme";
    private static final String SETTING_KEYBOARD_HEIGHT = "pref_key_app_profile_keyboard_height";

    /**
     * The multi-rule editor: one rule per line, {@code <package>=<tokens>}, using the same tokens
     * as the single rule above it. The list is mirrored into the private rule table on every
     * activation, so the single rule and the list keep working side by side.
     */
    private static final String SETTING_SAVED_RULES = "pref_key_app_profile_saved_rules";

    /** A package to delete from the list above; the field empties itself once the rule is gone. */
    private static final String SETTING_REMOVE_RULE = "pref_key_app_profile_remove_rule";

    /** Which rule keys were imported from the list, so an edited list can retire them again. */
    private static final String IMPORTED_PREFS_NAME = "gboard_app_profiles_imported";
    private static final String IMPORTED_KEYS = "keys";

    /** Preference keys Gboard's own toolbar editor and theme picker write. */
    private static final String PREF_KEY_TOOLBAR = "access_points_showing_order";
    private static final String PREF_KEY_TOOLBAR_FOLDABLE = "foldable_access_points_showing_order";
    private static final String PREF_KEY_THEME = "additional_keyboard_theme";

    /** Obfuscated classes of the writers, resolved by name so this runtime needs no Gboard symbols. */
    private static final String PREF_STORE_CLASS = "qpn";      // Lqpn; -> Lcei;, the preference store
    private static final String TOOLBAR_EDITOR_CLASS = "mog";  // Lmog; -> Lmob;, the toolbar editor
    private static final String DEVICE_CLASS = "nrx";          // Lnrx;->a()Lnru; current device
    private static final String DEVICE_TO_FOLDABLE = "foldable";

    private static final int FLAG_BIT_INCOGNITO = 1;
    private static final int FLAG_BIT_NO_SUGGESTIONS = 2;

    private static Context prefsContext;
    private static SharedPreferences prefs;
    private static Context settingsContext;
    private static SharedPreferences settingsPrefs;

    private static String activePackage;
    private static int ruleFlags;
    private static String ruleToolbar;
    private static String ruleTheme;
    private static String ruleHeight;
    private static boolean overrideSet;
    private static int overrideFlags;

    /** The package whose appearance overrides are currently written into Gboard's preferences. */
    private static String appliedProfilePackage;
    private static String savedToolbarOrder;
    private static String savedTheme;

    private AppProfileRuntime() {
    }

    /**
     * Called before Gboard's own activation body, while the editor metadata is already available
     * but the keyboard has not been drawn yet, so the profile is in place for the first frame.
     */
    public static void onActivate(Context context, EditorInfo info) {
        try {
            if (info == null) {
                return;
            }
            String pkg = info.packageName;
            if (pkg == null || pkg.length() == 0) {
                return;
            }
            if (!pkg.equals(activePackage)) {
                // A new package owns the keyboard now: the old session override is gone.
                activePackage = pkg;
                overrideSet = false;
                overrideFlags = 0;
            }
            // Keep the rule table in step with the list edited in Gboard's settings first: the
            // rule that applies to this field is read right after.
            syncSavedRules(context);
            String area = areaOf(info);
            String value = context == null ? null : ruleValue(context, pkg, area);
            ruleFlags = parseFlags(value);
            ruleToolbar = tokenValue(value, TOKEN_TOOLBAR);
            ruleTheme = tokenValue(value, TOKEN_THEME);
            ruleHeight = tokenValue(value, TOKEN_HEIGHT);
            // Appearance has to be written before the keyboard reads it for this frame.
            applyAppearance(context, pkg);
        } catch (Throwable ignored) {
            // An unreadable rule table leaves the stock keyboard alone.
        }
    }

    /** True when the package in focus asked for incognito behaviour. */
    public static boolean isIncognito() {
        return (currentFlags() & FLAG_BIT_INCOGNITO) != 0;
    }

    /** True when the package in focus asked for the suggestion strip to stay empty. */
    public static boolean isSuggestionBlocked() {
        return (currentFlags() & FLAG_BIT_NO_SUGGESTIONS) != 0;
    }

    /** Combines the profile with Gboard's own incognito state; a profile can only add to it. */
    public static boolean applyIncognito(boolean stockValue) {
        try {
            return stockValue || isIncognito();
        } catch (Throwable ignored) {
            return stockValue;
        }
    }

    /**
     * Candidate strip binding: an app whose profile blocks suggestions gets nothing to show.
     * The list handed back is immutable and empty, so the stock binding code runs as usual.
     */
    public static List applySuggestionPolicy(List candidates) {
        try {
            if (isSuggestionBlocked()) {
                return Collections.emptyList();
            }
        } catch (Throwable ignored) {
            // Fall through to the stock list.
        }
        return candidates;
    }

    /** Replaces the active package's rule until another package takes focus. */
    public static void overrideForSession(boolean incognito, boolean noSuggestions) {
        try {
            overrideFlags = (incognito ? FLAG_BIT_INCOGNITO : 0) |
                (noSuggestions ? FLAG_BIT_NO_SUGGESTIONS : 0);
            overrideSet = true;
        } catch (Throwable ignored) {
            // No override is the same as the rule.
        }
    }

    public static void clearSessionOverride() {
        overrideSet = false;
        overrideFlags = 0;
    }

    public static boolean hasSessionOverride() {
        return overrideSet;
    }

    /** The package the current profile belongs to, or null before the first activation. */
    public static String activePackage() {
        return activePackage;
    }

    /** Writes one rule. Kept for the editor that will live in Theme Studio. */
    public static void setRule(Context context, String key, boolean incognito, boolean noSuggestions) {
        try {
            if (key == null || key.length() == 0) {
                return;
            }
            SharedPreferences preferences = prefs(context);
            if (preferences == null) {
                return;
            }
            StringBuilder value = new StringBuilder();
            if (incognito) {
                value.append(FLAG_INCOGNITO);
            }
            if (noSuggestions) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(FLAG_NO_SUGGESTIONS);
            }
            if (value.length() == 0) {
                preferences.edit().remove(key).apply();
            } else {
                preferences.edit().putString(key, value.toString()).apply();
            }
        } catch (Throwable ignored) {
            // A rule that cannot be written leaves the previous one in place.
        }
    }

    /**
     * Mirrors the rule list from Gboard's settings into the private rule table.
     *
     * The list is the screen's only multi-rule editor: one line per rule, {@code <package>=<tokens>}
     * with the tokens the single rule uses, and a trailing {@code *} still makes a prefix rule. A
     * line without a value clears that package. Keys that came from an earlier list but are no
     * longer in it are removed; rules written by anything else - the single rule, or
     * {@link #setRule} and {@link #setAppearanceRule} - are left untouched.
     */
    private static void syncSavedRules(Context context) {
        try {
            SharedPreferences settings = defaultPreferences(context);
            if (settings == null) {
                return;
            }
            String text = settings.getString(SETTING_SAVED_RULES, null);
            String remove = settings.getString(SETTING_REMOVE_RULE, null);
            if (text == null && remove == null) {
                return;
            }
            SharedPreferences table = prefs(context);
            SharedPreferences imported = importedRules(context);
            if (table == null || imported == null) {
                return;
            }
            String edited = text == null ? "" : text;
            boolean listChanged = false;
            boolean pendingRemoval = remove != null && remove.trim().length() > 0;
            if (pendingRemoval) {
                String victim = remove.trim();
                table.edit().remove(victim).apply();
                String without = dropRuleLine(edited, victim);
                listChanged = !without.equals(edited);
                edited = without;
                settings.edit().putString(SETTING_REMOVE_RULE, "").apply();
            }

            java.util.LinkedHashMap<String, String> wanted =
                new java.util.LinkedHashMap<String, String>();
            for (String line : edited.split("\n")) {
                String entry = line.trim();
                if (entry.length() == 0 || entry.startsWith("#")) {
                    continue;
                }
                int separator = entry.indexOf('=');
                String key = (separator < 0 ? entry : entry.substring(0, separator)).trim();
                if (key.length() == 0) {
                    continue;
                }
                String value = separator < 0 ? "" : entry.substring(separator + 1).trim();
                wanted.put(key, value);
            }

            // This runs on every field focus, so a list that was already mirrored must leave the
            // preferences alone: same text, same keys and nothing to remove means the table is
            // already what the list says.
            String mirrored = imported.getString(IMPORTED_KEYS, "");
            if (!pendingRemoval && edited.equals(text == null ? "" : text) &&
                mirrored.equals(joinKeys(wanted.keySet()))) {
                return;
            }

            SharedPreferences.Editor editor = table.edit();
            for (String key : splitKeys(mirrored)) {
                if (!wanted.containsKey(key)) {
                    editor.remove(key);
                }
            }
            for (java.util.Map.Entry<String, String> rule : wanted.entrySet()) {
                if (rule.getValue().length() == 0) {
                    editor.remove(rule.getKey());
                } else {
                    editor.putString(rule.getKey(), rule.getValue());
                }
            }
            editor.apply();
            imported.edit().putString(IMPORTED_KEYS, joinKeys(wanted.keySet())).apply();
            if (listChanged) {
                settings.edit().putString(SETTING_SAVED_RULES, edited).apply();
            }
        } catch (Throwable ignored) {
            // An unreadable list leaves the rules it does not mention in place.
        }
    }

    /**
     * Refreshes the "Saved rules" row while Gboard's Preferences screen loads, so the rules the
     * list keeps and the packages they cover are visible without opening the field.
     *
     * Called from the same preference-group walk that sets the other custom summaries, so it runs
     * once per group, after the screen's preferences exist and before it is first drawn.
     */
    public static void refreshSavedRulesSummary(Object preferenceGroup) {
        try {
            visitPreferences(preferenceGroup);
        } catch (Throwable ignored) {
            // A row that cannot be refreshed keeps the explanation from the settings file.
        }
    }

    private static void visitPreferences(Object preference) {
        if (preference == null) {
            return;
        }
        if (SETTING_SAVED_RULES.equals(preferenceKey(preference))) {
            Context context = preferenceContext(preference);
            if (context != null) {
                String summary = savedRulesSummary(defaultPreferences(context));
                if (summary != null) {
                    setPreferenceSummary(preference, summary);
                }
            }
        }
        // A PreferenceGroup also has to be walked: the rules live in a category, not the root.
        int count = preferenceTreeSize(preference);
        for (int index = 0; index < count; index++) {
            visitPreferences(preferenceTreeChild(preference, index));
        }
    }

    /** "N rules: package, package …", showing the packages instead of the whole rule list. */
    private static String savedRulesSummary(SharedPreferences settings) {
        if (settings == null) {
            return null;
        }
        String text = settings.getString(SETTING_SAVED_RULES, "");
        List<String> keys = new ArrayList<String>();
        for (String line : text.split("\n")) {
            String entry = line.trim();
            if (entry.length() == 0 || entry.startsWith("#")) {
                continue;
            }
            int separator = entry.indexOf('=');
            String key = (separator < 0 ? entry : entry.substring(0, separator)).trim();
            if (key.length() > 0 && !keys.contains(key)) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return "No saved rules. Tap to add packages, one per line (package=tokens).";
        }
        StringBuilder summary = new StringBuilder();
        summary.append(keys.size()).append(keys.size() == 1 ? " rule: " : " rules: ");
        int shown = Math.min(keys.size(), 5);
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                summary.append(", ");
            }
            summary.append(keys.get(index));
        }
        if (keys.size() > shown) {
            summary.append(", +").append(keys.size() - shown).append(" more");
        }
        return summary.append(" (tap to edit)").toString();
    }

    /** The preference's key; Gboard's androidx.preference fork keeps it in an obfuscated field. */
    private static String preferenceKey(Object preference) {
        try {
            java.lang.reflect.Field key = namedField(preference.getClass(), "r");
            if (key != null) {
                Object value = key.get(preference);
                if (value instanceof String) {
                    return (String) value;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the public accessor.
        }
        try {
            Object value = preference.getClass().getMethod("getKey").invoke(preference);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context preferenceContext(Object preference) {
        try {
            java.lang.reflect.Field context = namedField(preference.getClass(), "j");
            if (context != null) {
                Object value = context.get(preference);
                if (value instanceof Context) {
                    return (Context) value;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the public accessor.
        }
        try {
            Object value = preference.getClass().getMethod("getContext").invoke(preference);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setPreferenceSummary(Object preference, CharSequence summary) {
        try {
            method(preference.getClass(), "V", CharSequence.class).invoke(preference, summary);
            return;
        } catch (Throwable ignored) {
            // Fall through to the public accessor.
        }
        try {
            method(preference.getClass(), "setSummary", CharSequence.class)
                .invoke(preference, summary);
        } catch (Throwable ignored) {
            // A row whose summary cannot be set keeps the text it had.
        }
    }

    private static int preferenceTreeSize(Object group) {
        try {
            return ((Integer) method(group.getClass(), "k").invoke(group)).intValue();
        } catch (Throwable ignored) {
            // Fall through to the public accessor.
        }
        try {
            return ((Integer) method(group.getClass(), "getPreferenceCount").invoke(group)).intValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object preferenceTreeChild(Object group, int index) {
        try {
            return method(group.getClass(), "o", int.class).invoke(group, index);
        } catch (Throwable ignored) {
            // Fall through to the public accessor.
        }
        try {
            return method(group.getClass(), "getPreference", int.class).invoke(group, index);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A declared field by name, in the class or its superclasses; obfuscated names are short. */
    private static java.lang.reflect.Field namedField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field found = current.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException ignored) {
                // Keep walking up.
            }
        }
        return null;
    }

    /** Removes the line for one package from the list text. */
    private static String dropRuleLine(String text, String ruleKey) {
        StringBuilder kept = new StringBuilder();
        for (String line : text.split("\n")) {
            String entry = line.trim();
            int separator = entry.indexOf('=');
            String key = (separator < 0 ? entry : entry.substring(0, separator)).trim();
            if (key.equals(ruleKey)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('\n');
            }
            kept.append(line);
        }
        return kept.toString();
    }

    private static List<String> splitKeys(String text) {
        List<String> keys = new ArrayList<String>();
        if (text == null) {
            return keys;
        }
        for (String key : text.split("\n")) {
            String trimmed = key.trim();
            if (trimmed.length() > 0) {
                keys.add(trimmed);
            }
        }
        return keys;
    }

    private static String joinKeys(java.util.Collection<String> keys) {
        StringBuilder joined = new StringBuilder();
        for (String key : keys) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(key);
        }
        return joined.toString();
    }

    /** The record of which table keys the settings list owns. */
    private static SharedPreferences importedRules(Context context) {
        try {
            if (context == null) {
                return null;
            }
            return applicationContext(context)
                .getSharedPreferences(IMPORTED_PREFS_NAME, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Writes the appearance part of one rule, through the same table {@link #setRule} uses. */
    public static void setAppearanceRule(Context context, String key, String toolbarOrder,
        String theme) {
        try {
            if (key == null || key.length() == 0) {
                return;
            }
            SharedPreferences preferences = prefs(context);
            if (preferences == null) {
                return;
            }
            String existing = preferences.getString(key, "");
            int flags = parseFlags(existing);
            String existingHeight = tokenValue(existing, TOKEN_HEIGHT);
            StringBuilder value = new StringBuilder();
            if ((flags & FLAG_BIT_INCOGNITO) != 0) {
                value.append(FLAG_INCOGNITO);
            }
            if ((flags & FLAG_BIT_NO_SUGGESTIONS) != 0) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(FLAG_NO_SUGGESTIONS);
            }
            if (toolbarOrder != null && toolbarOrder.trim().length() > 0) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(TOKEN_TOOLBAR).append(toolbarOrder.trim());
            }
            if (theme != null && theme.trim().length() > 0) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(TOKEN_THEME).append(theme.trim());
            }
            // The height is not part of this call's arguments, so keep the one already stored.
            if (existingHeight != null) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(TOKEN_HEIGHT).append(existingHeight);
            }
            if (value.length() == 0) {
                preferences.edit().remove(key).apply();
            } else {
                preferences.edit().putString(key, value.toString()).apply();
            }
        } catch (Throwable ignored) {
            // A rule that cannot be written leaves the previous one in place.
        }
    }

    public static void clearRule(Context context, String key) {
        try {
            SharedPreferences preferences = prefs(context);
            if (preferences != null && key != null) {
                preferences.edit().remove(key).apply();
            }
        } catch (Throwable ignored) {
            // Nothing to clear.
        }
    }

    private static int currentFlags() {
        return overrideSet ? overrideFlags : ruleFlags;
    }

    /**
     * The single rule that can be edited from Gboard's own Preferences screen. The package field is
     * the whole rule: empty means no rule, a trailing {@code *} makes it a prefix, and an optional
     * {@code #area} suffix (text, url, email, password, number) limits it to one kind of field. The
     * toggles and the toolbar/theme fields below it become the value of that rule.
     */
    private static String settingsValue(Context context, String pkg, String area) {
        try {
            SharedPreferences preferences = defaultPreferences(context);
            if (preferences == null) {
                return null;
            }
            String rule = preferences.getString(SETTING_PACKAGE, null);
            if (rule == null) {
                return null;
            }
            rule = rule.trim();
            if (rule.length() == 0) {
                return null;
            }
            String pattern = rule;
            String areaFilter = null;
            int separator = rule.indexOf(AREA_SEPARATOR);
            if (separator >= 0) {
                pattern = rule.substring(0, separator).trim();
                areaFilter = rule.substring(separator + 1).trim();
            }
            if (pattern.length() == 0) {
                return null;
            }
            boolean matches;
            if (pattern.endsWith(PREFIX_SUFFIX)) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                matches = prefix.length() > 0 && pkg.startsWith(prefix);
            } else {
                matches = pkg.equals(pattern);
            }
            if (!matches) {
                return null;
            }
            if (areaFilter != null && areaFilter.length() > 0 &&
                !areaFilter.equalsIgnoreCase(area)) {
                return null;
            }
            StringBuilder value = new StringBuilder();
            if (preferences.getBoolean(SETTING_INCOGNITO, false)) {
                value.append(FLAG_INCOGNITO);
            }
            if (preferences.getBoolean(SETTING_NO_SUGGESTIONS, false)) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(FLAG_NO_SUGGESTIONS);
            }
            appendToken(value, TOKEN_TOOLBAR, preferences.getString(SETTING_TOOLBAR, null));
            appendToken(value, TOKEN_THEME, preferences.getString(SETTING_THEME, null));
            appendToken(value, TOKEN_HEIGHT, preferences.getString(SETTING_KEYBOARD_HEIGHT, null));
            return value.length() == 0 ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void appendToken(StringBuilder value, String token, String argument) {
        if (argument == null) {
            return;
        }
        String trimmed = argument.trim();
        if (trimmed.length() == 0) {
            return;
        }
        if (value.length() > 0) {
            value.append(',');
        }
        value.append(token).append(trimmed);
    }

    /**
     * Writes the appearance half of a profile into Gboard's own preferences, and puts back what
     * was there before once the package that asked for it is no longer in focus.
     */
    private static void applyAppearance(Context context, String pkg) {
        if (context == null) {
            return;
        }
        boolean hasToolbar = ruleToolbar != null && ruleToolbar.length() > 0;
        boolean hasTheme = ruleTheme != null && ruleTheme.length() > 0;
        boolean hasHeight = ruleHeight != null && ruleHeight.length() > 0;
        if (appliedProfilePackage != null && !appliedProfilePackage.equals(pkg)) {
            restoreAppearance(context);
        }
        if (!hasToolbar && !hasTheme && !hasHeight) {
            return;
        }
        if (!pkg.equals(appliedProfilePackage)) {
            // Remember the keyboard's own values so the next app gets them back unchanged.
            savedToolbarOrder = readPreference(context, toolbarKey(context));
            savedTheme = readPreference(context, PREF_KEY_THEME);
            appliedProfilePackage = pkg;
        }
        if (hasTheme) {
            writePreference(context, PREF_KEY_THEME, ruleTheme);
        }
        // A height is a theme spec in the same preference, so it is written last: when both are
        // set the height is the one the keyboard ends up using.
        if (hasHeight) {
            writePreference(context, PREF_KEY_THEME, ruleHeight);
        }
        if (hasToolbar) {
            applyToolbarOrder(context, ruleToolbar);
        }
    }

    private static void restoreAppearance(Context context) {
        try {
            if (savedTheme != null) {
                writePreference(context, PREF_KEY_THEME, savedTheme);
            }
            if (savedToolbarOrder != null) {
                if (savedToolbarOrder.length() == 0) {
                    removePreference(context, toolbarKey(context));
                } else {
                    applyToolbarOrder(context, savedToolbarOrder);
                }
            }
        } catch (Throwable ignored) {
            // A failed restore leaves the stock value in place.
        } finally {
            appliedProfilePackage = null;
            savedTheme = null;
            savedToolbarOrder = null;
        }
    }

    /**
     * The toolbar editor's own writer: {@code mog.p(Context, Lnru, List)} joins the ids with
     * {@code ;} and stores them under the key for the current device, exactly like the toolbar
     * editor does, so the bar is read back by the stock reader with no extra code.
     */
    private static void applyToolbarOrder(Context context, String order) {
        try {
            if (order == null || order.length() == 0) {
                return;
            }
            String joined = order;
            if (joined.equals(readPreference(context, toolbarKey(context)))) {
                return;
            }
            List<String> ids = new ArrayList<String>();
            for (String id : order.split(";")) {
                String trimmed = id.trim();
                if (trimmed.length() > 0) {
                    ids.add(trimmed);
                }
            }
            if (ids.isEmpty()) {
                return;
            }
            Class<?> deviceType = Class.forName(DEVICE_CLASS);
            Object device = method(deviceType, "a").invoke(null);
            if (device == null) {
                return;
            }
            // The method takes the enum value itself (Lnru), not the class that hands it out (Lnrx).
            Class<?> editorType = Class.forName(TOOLBAR_EDITOR_CLASS);
            method(editorType, "p", Context.class, device.getClass(), List.class)
                .invoke(null, applicationContext(context), device, ids);
        } catch (Throwable ignored) {
            // A profile without a usable order keeps the stock toolbar.
        }
    }

    /** The preference key the toolbar editor writes for this device. */
    private static String toolbarKey(Context context) {
        try {
            Class<?> deviceType = Class.forName(DEVICE_CLASS);
            Object device = method(deviceType, "a").invoke(null);
            if (device != null && DEVICE_TO_FOLDABLE.equals(String.valueOf(device))) {
                return PREF_KEY_TOOLBAR_FOLDABLE;
            }
        } catch (Throwable ignored) {
            // Fall through to the phone key, which is what the stock reader falls back to.
        }
        return PREF_KEY_TOOLBAR;
    }

    /** Reads through Gboard's preference store ({@code qpn}, i.e. {@code Lcei;->d}). */
    private static String readPreference(Context context, String key) {
        try {
            Object store = preferenceStore(context);
            if (store == null || key == null) {
                return null;
            }
            Object value = method(store.getClass(), "d", String.class, String.class)
                .invoke(store, key, "");
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Writes through Gboard's preference store ({@code Lcei;->i}), so its cache stays coherent. */
    private static void writePreference(Context context, String key, String value) {
        try {
            String desired = value == null ? "" : value;
            String current = readPreference(context, key);
            if (current != null && current.equals(desired)) {
                return;
            }
            if (desired.length() == 0) {
                removePreference(context, key);
                return;
            }
            Object store = preferenceStore(context);
            if (store == null) {
                return;
            }
            method(store.getClass(), "i", String.class, String.class)
                .invoke(store, key, desired);
        } catch (Throwable ignored) {
            // A profile without a usable theme keeps the stock theme.
        }
    }

    private static void removePreference(Context context, String key) {
        try {
            Object store = preferenceStore(context);
            if (store == null || key == null) {
                return;
            }
            method(store.getClass(), "w", String.class).invoke(store, key);
        } catch (Throwable ignored) {
            // Nothing to remove.
        }
    }

    /** {@code qpn.I(Context)}: the preference store Gboard's own readers and writers use. */
    private static Object preferenceStore(Context context) {
        try {
            if (context == null) {
                return null;
            }
            Class<?> storeType = Class.forName(PREF_STORE_CLASS);
            return method(storeType, "I", Context.class).invoke(null, applicationContext(context));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Finds a method in the class or its superclasses; the base class may not be public. */
    private static java.lang.reflect.Method method(Class<?> type, String name,
        Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Method found = current.getDeclaredMethod(name, parameters);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException ignored) {
                // Keep walking up.
            }
        }
        throw new NoSuchMethodException(type.getName() + "->" + name);
    }

    private static Context applicationContext(Context context) {
        Context app = context.getApplicationContext();
        return app == null ? context : app;
    }

    /**
     * Gboard keeps its preferences in the device-protected storage when it can; the settings screen
     * writes there, so the rule has to be read from the same place before falling back.
     */
    private static SharedPreferences defaultPreferences(Context context) {
        try {
            Context base = applicationContext(context);
            if (settingsPrefs != null && settingsContext == base) {
                return settingsPrefs;
            }
            SharedPreferences deviceProtected = null;
            deviceProtected = deviceProtectedPreferences(base);
            SharedPreferences fallback = PreferenceManager.getDefaultSharedPreferences(base);
            SharedPreferences chosen = fallback;
            if (deviceProtected != null &&
                (deviceProtected.contains(SETTING_PACKAGE) ||
                    deviceProtected.contains(SETTING_INCOGNITO) ||
                    deviceProtected.contains(SETTING_NO_SUGGESTIONS) ||
                    deviceProtected.contains(SETTING_TOOLBAR) ||
                    deviceProtected.contains(SETTING_THEME) ||
                    deviceProtected.contains(SETTING_KEYBOARD_HEIGHT) ||
                    deviceProtected.contains(SETTING_SAVED_RULES) ||
                    deviceProtected.contains(SETTING_REMOVE_RULE))) {
                chosen = deviceProtected;
            }
            settingsPrefs = chosen;
            settingsContext = base;
            return chosen;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The device-protected preference file, reached by reflection because the API 24 context methods
     * are not in the API 16 stubs this runtime is compiled against.
     */
    private static SharedPreferences deviceProtectedPreferences(Context base) {
        try {
            java.lang.reflect.Method isDeviceProtected =
                Context.class.getMethod("isDeviceProtectedStorage");
            if (Boolean.TRUE.equals(isDeviceProtected.invoke(base))) {
                return PreferenceManager.getDefaultSharedPreferences(base);
            }
            java.lang.reflect.Method createDeviceProtected =
                Context.class.getMethod("createDeviceProtectedStorageContext");
            Object deviceContext = createDeviceProtected.invoke(base);
            if (deviceContext instanceof Context) {
                return PreferenceManager.getDefaultSharedPreferences((Context) deviceContext);
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Field kind used by {@code <package>#<area>} rules. */
    private static String areaOf(EditorInfo info) {
        int type = info.inputType;
        int cls = type & EditorInfo.TYPE_MASK_CLASS;
        if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
            return "number";
        }
        if (cls != EditorInfo.TYPE_CLASS_TEXT) {
            return "other";
        }
        int variation = type & EditorInfo.TYPE_MASK_VARIATION;
        switch (variation) {
            case EditorInfo.TYPE_TEXT_VARIATION_URI:
                return "url";
            case EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
            case EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                return "email";
            case EditorInfo.TYPE_TEXT_VARIATION_PASSWORD:
            case EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
            case EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                return "password";
            default:
                return "text";
        }
    }

    /** The most specific rule value for this package and field, table first, settings second. */
    private static String ruleValue(Context context, String pkg, String area) {
        String table = lookupValue(context, pkg, area);
        String settings = settingsValue(context, pkg, area);
        if (table == null) {
            return settings;
        }
        if (settings == null) {
            return table;
        }
        return table + "," + settings;
    }

    private static String lookupValue(Context context, String pkg, String area) {
        SharedPreferences preferences = prefs(context);
        if (preferences == null) {
            return null;
        }
        Map<String, ?> rules = preferences.getAll();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        Object value = rules.get(pkg + AREA_SEPARATOR + area);
        if (value instanceof String) {
            return (String) value;
        }
        value = rules.get(pkg);
        if (value instanceof String) {
            return (String) value;
        }
        String bestPrefix = null;
        for (String key : rules.keySet()) {
            if (key == null || key.length() < 2 || !key.endsWith(PREFIX_SUFFIX)) {
                continue;
            }
            String prefix = key.substring(0, key.length() - 1);
            if (!pkg.startsWith(prefix)) {
                continue;
            }
            if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                bestPrefix = prefix;
            }
        }
        if (bestPrefix == null) {
            return null;
        }
        value = rules.get(bestPrefix + PREFIX_SUFFIX);
        return value instanceof String ? (String) value : null;
    }

    private static int parseFlags(String value) {
        int flags = 0;
        if (value == null) {
            return 0;
        }
        for (String part : value.split(",")) {
            String flag = part.trim().toLowerCase(Locale.ROOT);
            if (FLAG_INCOGNITO.equals(flag)) {
                flags |= FLAG_BIT_INCOGNITO;
            } else if (FLAG_NO_SUGGESTIONS.equals(flag)) {
                flags |= FLAG_BIT_NO_SUGGESTIONS;
            }
        }
        return flags;
    }

    /** The argument of a {@code toolbar:…} or {@code theme:…} token, or null when absent. */
    private static String tokenValue(String value, String token) {
        if (value == null) {
            return null;
        }
        for (String part : value.split(",")) {
            String candidate = part.trim();
            if (candidate.regionMatches(true, 0, token, 0, token.length())) {
                String argument = candidate.substring(token.length()).trim();
                if (argument.length() > 0) {
                    return argument;
                }
            }
        }
        return null;
    }

    /** The private rule table, cached per application context. */
    private static SharedPreferences prefs(Context context) {
        try {
            if (context == null) {
                return null;
            }
            Context base = applicationContext(context);
            if (prefs == null || prefsContext != base) {
                prefs = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefsContext = base;
            }
            return prefs;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
