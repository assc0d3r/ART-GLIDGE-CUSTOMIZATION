package dev.custom.gboardpatches.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.List;
import java.util.Map;

public final class KeyboardThemeRuntime {
    private static final String TAG = "GboardThemeStudio";
    private static volatile Map<String, Object> cachedTheme;
    private static volatile String cachedThemeId;

    private KeyboardThemeRuntime() {}

    public static void initializePreferences(Object preferenceGroup) {
        if (preferenceGroup == null) return;
        List<Object> preferences = new java.util.ArrayList<>();
        collectPreferences(preferenceGroup, preferences);
        for (Object preference : preferences) {
            if (!KeyboardThemeStudioActivity.PREF_KEY_STUDIO.equals(GlideTrailPreferences.getKey(preference))) {
                continue;
            }
            installStudioListener(preference);
        }
    }

    public static void applyKey(View keyView, Object softKeyDef) {
        if (keyView == null || softKeyDef == null) return;
        try {
            if ("builtin_default".equals(ThemeStore.activeId(keyView.getContext()))) return;
            Map<String, Object> theme = theme(keyView.getContext());
            String description = keyView.getContentDescription() == null
                ? String.valueOf(softKeyDef)
                : keyView.getContentDescription().toString();
            String lower = description.toLowerCase(java.util.Locale.ROOT);
            boolean enter = keyView.getClass().getName().contains("EnterSoftKeyView") ||
                lower.contains("enter") || lower.contains("return");
            boolean modifier = !enter && (lower.contains("shift") || lower.contains("delete") ||
                lower.contains("backspace") || lower.contains("symbol") || lower.contains("language") ||
                lower.contains("globe") || lower.contains("emoji") || lower.contains("voice"));

            int defaultText = color(theme, "keyText", 0xFFE9E9EE);
            int keyText = enter ? color(theme, "enterKeyText", defaultText)
                : modifier ? color(theme, "modifierKeyText", defaultText)
                : defaultText;
            int background = enter ? color(theme, "enterKeyBackground", 0xFF4C8DF6)
                : modifier ? color(theme, "modifierKeyBackground", 0xFF222428)
                : color(theme, "keyBackground", 0xFF303338);
            int pressed = color(theme, "pressedKeyBackground", blend(background, color(theme, "accent", 0xFF8AB4F8)));
            int border = color(theme, "keyBorderColor", 0);
            float density = keyView.getResources().getDisplayMetrics().density;
            float radius = (float) ThemeDocument.number(theme, "keyCornerRadiusDp", 6) * density;
            float borderWidth = (float) ThemeDocument.number(theme, "keyBorderWidthDp", 0) * density;
            String shape = ThemeDocument.string(theme, "keyShape", "ROUNDED");

            keyView.setBackground(new ThemeKeyDrawable(background, pressed, border, borderWidth, radius, shape));
            setTextColors(keyView, keyText);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply the selected Gboard theme to a key.", exception);
        }
    }

    public static void applyBoard(View boardView) {
        if (boardView == null) return;
        try {
            if ("builtin_default".equals(ThemeStore.activeId(boardView.getContext()))) return;
            Map<String, Object> theme = theme(boardView.getContext());
            int color = color(theme, "boardBackground", 0xFF17181C);
            Object gradientValue = theme.get("boardGradient");
            if (gradientValue instanceof Map) {
                List<Object> colors = ThemeDocument.array(ThemeDocument.object(gradientValue).get("colors"));
                int[] stops = new int[Math.min(colors.size(), 12)];
                for (int i = 0; i < stops.length; i++) {
                    Object stop = colors.get(i);
                    Map<String, Object> stopObject = new java.util.HashMap<>();
                    stopObject.put("value", stop);
                    stops[i] = ThemeDocument.color(stopObject, "value", color);
                }
                if (stops.length >= 2) {
                    GradientDrawable.Orientation orientation = gradientOrientation(gradientValue);
                    boardView.setBackground(new GradientDrawable(orientation, stops));
                    return;
                }
            }
            boardView.setBackgroundColor(color);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply the selected Gboard board theme.", exception);
        }
    }

    static void invalidate() {
        cachedTheme = null;
        cachedThemeId = null;
    }

    private static Map<String, Object> theme(Context context) {
        String id = ThemeStore.activeId(context);
        Map<String, Object> current = cachedTheme;
        if (current != null && id.equals(cachedThemeId)) return current;
        synchronized (KeyboardThemeRuntime.class) {
            if (cachedTheme == null || !id.equals(cachedThemeId)) {
                cachedTheme = ThemeStore.active(context);
                cachedThemeId = id;
            }
            return cachedTheme;
        }
    }

    private static int color(Map<String, Object> theme, String key, int fallback) {
        return ThemeDocument.color(theme, key, fallback);
    }

    private static int blend(int first, int second) {
        return android.graphics.Color.argb(
            (android.graphics.Color.alpha(first) + android.graphics.Color.alpha(second)) / 2,
            (android.graphics.Color.red(first) + android.graphics.Color.red(second)) / 2,
            (android.graphics.Color.green(first) + android.graphics.Color.green(second)) / 2,
            (android.graphics.Color.blue(first) + android.graphics.Color.blue(second)) / 2
        );
    }

    private static GradientDrawable.Orientation gradientOrientation(Object gradient) {
        float angle = (float) ThemeDocument.number(ThemeDocument.object(gradient), "angleDeg", 45);
        if (angle < 45 || angle >= 315) return GradientDrawable.Orientation.LEFT_RIGHT;
        if (angle < 135) return GradientDrawable.Orientation.BOTTOM_TOP;
        if (angle < 225) return GradientDrawable.Orientation.RIGHT_LEFT;
        return GradientDrawable.Orientation.TOP_BOTTOM;
    }

    private static void setTextColors(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setTextColors(group.getChildAt(index), color);
        }
    }

    private static void collectPreferences(Object preference, List<Object> output) {
        if (preference == null) return;
        output.add(preference);
        java.lang.reflect.Method count = findMethod(preference.getClass(), "getPreferenceCount");
        if (count == null) count = findMethod(preference.getClass(), "k");
        java.lang.reflect.Method child = findMethod(preference.getClass(), "getPreference", int.class);
        if (child == null) child = findMethod(preference.getClass(), "o", int.class);
        if (count == null || child == null) return;
        try {
            int size = ((Number) count.invoke(preference)).intValue();
            for (int index = 0; index < size; index++) collectPreferences(child.invoke(preference, index), output);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            Log.w(TAG, "Unable to inspect Gboard's theme settings.", exception);
        }
    }

    private static void installStudioListener(final Object preference) {
        try {
            ClassLoader loader = preference.getClass().getClassLoader();
            Class<?> type = Class.forName("androidx.preference.Preference$OnPreferenceClickListener", false, loader);
            Object listener = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if ("toString".equals(method.getName())) return "ThemeStudioPreferenceListener";
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName())) return proxy == (args == null ? null : args[0]);
                    }
                    if ("onPreferenceClick".equals(method.getName())) {
                        Context context = GlideTrailPreferences.getContext(preference);
                        if (context != null) {
                            android.content.Intent intent = new android.content.Intent(
                                context,
                                KeyboardThemeStudioActivity.class
                            );
                            if (!(context instanceof android.app.Activity)) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            }
                            context.startActivity(intent);
                        }
                        return true;
                    }
                    return null;
                });
            java.lang.reflect.Method setter = findMethod(
                preference.getClass(),
                "setOnPreferenceClickListener",
                type
            );
            if (setter == null) throw new NoSuchMethodException("setOnPreferenceClickListener");
            setter.invoke(preference, listener);
        } catch (ReflectiveOperationException | LinkageError exception) {
            Log.e(TAG, "Unable to initialize the theme studio preference.", exception);
        }
    }

    private static java.lang.reflect.Method findMethod(Class<?> type, String name, Class<?>... parameters) {
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
}
