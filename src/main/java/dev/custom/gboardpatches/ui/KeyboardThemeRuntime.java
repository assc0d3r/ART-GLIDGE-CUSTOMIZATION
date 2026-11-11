package dev.custom.gboardpatches.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * Applies the saved Gboard key theme and opens the Theme Builder from its settings entry.
 *
 * Gboard ships an obfuscated androidx.preference, so member names are resolved by signature
 * shape instead of by name.
 */
public final class KeyboardThemeRuntime {

    public static final String PREFS = "gboard_theme_studio";
    public static final String KEY_ENABLED = "theme_enabled";
    public static final String KEY_ACCENT = "theme_accent";
    public static final String KEY_KEY_BG = "theme_key_background";
    public static final String KEY_KEY_TEXT = "theme_key_text";
    public static final String KEY_RADIUS = "theme_corner_radius";

    public static final int DEFAULT_ACCENT = 0xFF4285F4;
    public static final int DEFAULT_KEY_BG = 0xFF202124;
    public static final int DEFAULT_KEY_TEXT = 0xFFFFFFFF;
    public static final int DEFAULT_RADIUS = 8;

    private static final String THEME_PREF_KEY = "pref_key_gboard_theme_studio";

    private static long lastLoad = 0L;
    private static boolean enabled = false;
    private static int accent = DEFAULT_ACCENT;
    private static int keyBackground = DEFAULT_KEY_BG;
    private static int keyText = DEFAULT_KEY_TEXT;
    private static int radius = DEFAULT_RADIUS;

    private KeyboardThemeRuntime() {
    }

    public static void initializePreferences(Object preferenceGroup) {
        try {
            Object preference = findPreference(preferenceGroup, THEME_PREF_KEY);
            if (preference == null) {
                return;
            }
            attachClickListener(preference);
        } catch (Throwable ignored) {
        }
    }

    public static void applyKey(View keyView, Object softKeyDef) {
        try {
            if (keyView == null) {
                return;
            }
            Context context = keyView.getContext();
            if (context == null) {
                return;
            }
            refresh(context);
            if (!enabled) {
                return;
            }
            GradientDrawable background = new GradientDrawable();
            background.setColor(keyBackground);
            background.setCornerRadius(radius * keyView.getResources().getDisplayMetrics().density);
            background.setStroke(2, accent);
            keyView.setBackgroundDrawable(background);
            if (keyView instanceof TextView) {
                ((TextView) keyView).setTextColor(keyText);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void refresh(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastLoad < 1000L) {
            return;
        }
        lastLoad = now;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            enabled = prefs.getBoolean(KEY_ENABLED, false);
            accent = prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT);
            keyBackground = prefs.getInt(KEY_KEY_BG, DEFAULT_KEY_BG);
            keyText = prefs.getInt(KEY_KEY_TEXT, DEFAULT_KEY_TEXT);
            radius = prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS);
        } catch (Throwable ignored) {
        }
    }

    private static Object findPreference(Object group, String key) {
        if (group == null) {
            return null;
        }
        Class<?> type = group.getClass();
        while (type != null) {
            Method[] methods = type.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Class<?>[] params = methods[i].getParameterTypes();
                if (params.length == 1
                    && params[0] == CharSequence.class
                    && "androidx.preference.Preference".equals(
                        methods[i].getReturnType().getName())) {
                    try {
                        methods[i].setAccessible(true);
                        Object found = methods[i].invoke(group, key);
                        if (found != null) {
                            return found;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static void attachClickListener(final Object preference) throws Exception {
        Class<?> preferenceType = preference.getClass();
        Class<?> listenerType = null;
        Method setter = null;

        Class<?> type = preferenceType;
        while (type != null && setter == null) {
            Method[] methods = type.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Class<?>[] params = methods[i].getParameterTypes();
                if (params.length == 1
                    && params[0].getName().endsWith("OnPreferenceClickListener")) {
                    listenerType = params[0];
                    setter = methods[i];
                    break;
                }
            }
            type = type.getSuperclass();
        }
        if (setter == null || listenerType == null) {
            return;
        }

        Object listener = java.lang.reflect.Proxy.newProxyInstance(
            listenerType.getClassLoader(),
            new Class<?>[]{listenerType},
            new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    try {
                        Object target = args != null && args.length > 0 ? args[0] : preference;
                        launch(target);
                    } catch (Throwable ignored) {
                    }
                    return Boolean.TRUE;
                }
            }
        );
        setter.setAccessible(true);
        setter.invoke(preference, listener);
    }

    private static void launch(Object preference) {
        Context context = resolveContext(preference);
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, KeyboardThemeStudioActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static Context resolveContext(Object preference) {
        if (preference instanceof View) {
            return ((View) preference).getContext();
        }
        if (preference == null) {
            return null;
        }
        Class<?> type = preference.getClass();
        while (type != null) {
            Method[] methods = type.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getParameterTypes().length == 0
                    && methods[i].getReturnType() == Context.class) {
                    try {
                        methods[i].setAccessible(true);
                        Object value = methods[i].invoke(preference);
                        if (value instanceof Context) {
                            return (Context) value;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
