package dev.custom.gboardpatches.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Bridge helper for managing Gboard Glide Trail preferences.
 * Uses robust reflection to handle obfuscated AndroidX Preference fields and methods.
 */
public class GlideTrailPreferences {
    private static final String TAG = "GboardCustomizations";
    private static final Object LISTENER_LOCK = new Object();
    private static final WeakHashMap<Object, Boolean> PREFERENCE_LISTENERS = new WeakHashMap<>();

    public interface PreferenceClickHandler {
        boolean onClick(Object preference);
    }

    public interface PreferenceChangeHandler {
        boolean onChange(Object preference, Object value);
    }

    public static final String PREF_MASTER_ENABLED = "pref_key_glide_trail_custom_enabled";
    public static final String PREF_RAINBOW = "pref_key_glide_trail_rainbow";
    public static final String PREF_COLOR_WHEEL = "pref_key_glide_trail_color_wheel";
    public static final String PREF_CUSTOM_COLOR = "pref_key_glide_trail_custom_color";
    public static final String PREF_SPEED_MS = "pref_key_glide_trail_speed_ms";
    public static final String PREF_WIDTH_DP = "pref_key_glide_trail_width_dp";
    public static final String PREF_LENGTH_PTS = "pref_key_glide_trail_length_pts";

    /**
     * Called from Lcdr;->aA(Landroidx/preference/Preference;)Z when a preference is clicked.
     * @return true if the click was handled by our custom dialogs, false to proceed normally.
     */
    public static boolean onPreferenceClick(Object preference) {
        if (preference == null) return false;
        try {
            String key = getKey(preference);
            if (key == null) return false;

            if (PREF_COLOR_WHEEL.equals(key)) {
                Context context = getContext(preference);
                if (context != null) {
                    ColorWheelDialog.show(context, preference);
                }
                return true;
            }

            if (PREF_SPEED_MS.equals(key) || PREF_WIDTH_DP.equals(key) || PREF_LENGTH_PTS.equals(key)) {
                Context context = getContext(preference);
                if (context != null) {
                    GlideTrailSliderDialog.show(context, preference, key);
                }
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Called during PreferenceGroup traversal in Ldoe;->aB to set up active summaries.
     */
    public static void initSummaries(Object preferenceGroup) {
        if (preferenceGroup == null) return;
        try {
            traverseAndInit(preferenceGroup);
        } catch (Throwable ignored) {}
    }

    private static void traverseAndInit(Object preference) {
        if (preference == null) return;
        try {
            String key = getKey(preference);
            if (key != null) {
                Context context = getContext(preference);
                if (context != null) {
                    SharedPreferences prefs = getPrefs(context);
                    if (PREF_COLOR_WHEEL.equals(key)) {
                        int color = prefs.getInt(PREF_CUSTOM_COLOR, 0);
                        if (color != 0) {
                            setSummary(preference, "Active color: " + String.format("#%06X", (0xFFFFFF & color)) + " (Tap to change)");
                        } else {
                            setSummary(preference, "Using Gboard stock dynamic theme color (Tap to set custom)");
                        }
                    } else if (PREF_SPEED_MS.equals(key)) {
                        int ms = prefs.getInt(PREF_SPEED_MS, 1000);
                        setSummary(preference, "Current: " + ms + " ms (Tap to adjust)");
                    } else if (PREF_WIDTH_DP.equals(key)) {
                        int dp = prefs.getInt(PREF_WIDTH_DP, 13);
                        setSummary(preference, "Current: " + dp + " dp (Tap to adjust)");
                    } else if (PREF_LENGTH_PTS.equals(key)) {
                        int pts = prefs.getInt(PREF_LENGTH_PTS, 20);
                        setSummary(preference, "Current: " + pts + " points (Tap to adjust)");
                    }
                }
            }

            // Check if this preference is a PreferenceGroup with children
            Method getCountMethod = findMethod(preference.getClass(), "k");
            if (getCountMethod == null) {
                getCountMethod = findMethod(preference.getClass(), "getPreferenceCount");
            }
            Method getPrefMethod = findMethod(preference.getClass(), "o", int.class);
            if (getPrefMethod == null) {
                getPrefMethod = findMethod(preference.getClass(), "getPreference", int.class);
            }

            if (getCountMethod != null && getPrefMethod != null) {
                int count = (Integer) getCountMethod.invoke(preference);
                for (int i = 0; i < count; i++) {
                    Object child = getPrefMethod.invoke(preference, i);
                    traverseAndInit(child);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String getKey(Object preference) {
        if (preference == null) return null;
        // Prefer stable framework APIs; obfuscated fields can change between Gboard releases.
        try {
            Method m = findMethod(preference.getClass(), "getKey");
            if (m != null) {
                Object val = m.invoke(preference);
                if (val instanceof String) return (String) val;
            }
        } catch (Throwable ignored) {}

        // Fall back to the currently observed Gboard field mapping.
        try {
            Field f = getField(preference.getClass(), "r");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(preference);
                if (val instanceof String) return (String) val;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static Boolean getPreferenceBoolean(Object preference) {
        if (preference == null) return null;
        try {
            Method method = findMethod(preference.getClass(), "isChecked");
            if (method == null) return null;
            Object value = method.invoke(preference);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Unable to read a Gboard customization switch.", exception);
            return null;
        }
    }

    public static Context getContext(Object preference) {
        if (preference == null) return null;
        // Prefer the framework accessor before relying on an obfuscated implementation detail.
        try {
            Method m = findMethod(preference.getClass(), "getContext");
            if (m != null) {
                Object val = m.invoke(preference);
                if (val instanceof Context) return (Context) val;
            }
        } catch (Throwable ignored) {}

        // Fall back to the currently observed Gboard field mapping.
        try {
            Field f = getField(preference.getClass(), "j");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(preference);
                if (val instanceof Context) return (Context) val;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static void setPreferenceClickListener(
        Object preference,
        final PreferenceClickHandler handler
    ) {
        setPreferenceListener(preference, 1, new PreferenceInvocationHandler() {
            @Override
            public boolean invoke(Object target, Object[] arguments) {
                return handler.onClick(target);
            }
        });
    }

    public static void setPreferenceChangeListener(
        Object preference,
        final PreferenceChangeHandler handler
    ) {
        setPreferenceListener(preference, 2, new PreferenceInvocationHandler() {
            @Override
            public boolean invoke(Object target, Object[] arguments) {
                return handler.onChange(target, arguments.length == 1 ? arguments[0] : arguments[1]);
            }
        }, true);
    }

    private interface PreferenceInvocationHandler {
        boolean invoke(Object preference, Object[] arguments);
    }

    private static void setPreferenceListener(
        final Object preference,
        final int callbackArgumentCount,
        final PreferenceInvocationHandler callback
    ) {
        setPreferenceListener(preference, callbackArgumentCount, callback, false);
    }

    private static void setPreferenceListener(
        final Object preference,
        final int callbackArgumentCount,
        final PreferenceInvocationHandler callback,
        final boolean objectValueFieldFallback
    ) {
        if (preference == null) return;
        synchronized (LISTENER_LOCK) {
            if (PREFERENCE_LISTENERS.containsKey(preference)) return;
        }

        try {
            Method setter = findPreferenceListenerSetter(preference.getClass(), callbackArgumentCount);
            Field listenerField = null;
            final Class<?> listenerType;
            if (setter != null) {
                listenerType = setter.getParameterTypes()[0];
            } else {
                listenerField = findPreferenceListenerField(
                    preference.getClass(),
                    callbackArgumentCount,
                    objectValueFieldFallback
                );
                if (listenerField == null) {
                    throw new NoSuchMethodException("Preference listener callback was not found");
                }
                listenerType = listenerField.getType();
            }
            final boolean objectValueListener =
                setter == null && objectValueFieldFallback;
            final Method callbackMethod = findBooleanCallback(
                listenerType,
                objectValueListener ? 1 : callbackArgumentCount,
                objectValueListener ? Object.class : preference.getClass(),
                objectValueListener
            );
            if (callbackMethod == null) {
                throw new NoSuchMethodException("Preference listener callback was not found");
            }
            Object listener = Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[] { listenerType },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(method.getName())) return "GboardCustomPreferenceListener";
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("equals".equals(method.getName())) {
                                return arguments != null && arguments.length == 1 && proxy == arguments[0];
                            }
                        }
                        if (isCallbackMethod(method, callbackMethod) && arguments != null) {
                            if (arguments.length == callbackArgumentCount) {
                                Object target = arguments[0] == null ? preference : arguments[0];
                                return callback.invoke(target, arguments);
                            }
                            if (objectValueFieldFallback && arguments.length == 1) {
                                return callback.invoke(preference, arguments);
                            }
                        }
                        return defaultValue(method.getReturnType());
                    }
                }
            );
            if (setter != null) {
                setter.setAccessible(true);
                setter.invoke(preference, listener);
            } else {
                listenerField.setAccessible(true);
                listenerField.set(preference, listener);
            }
            synchronized (LISTENER_LOCK) {
                PREFERENCE_LISTENERS.put(preference, Boolean.TRUE);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            Log.e(TAG, "Unable to initialize a Gboard customization preference.", exception);
        }
    }

    private static boolean isCallbackMethod(Method method, Method callbackMethod) {
        if (
            !method.getName().equals(callbackMethod.getName()) ||
            method.getReturnType() != callbackMethod.getReturnType()
        ) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        Class<?>[] callbackParameters = callbackMethod.getParameterTypes();
        if (parameters.length != callbackParameters.length) return false;
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index] != callbackParameters[index]) return false;
        }
        return true;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return Boolean.FALSE;
        if (returnType == Character.TYPE) return Character.valueOf('\0');
        if (returnType == Byte.TYPE) return Byte.valueOf((byte) 0);
        if (returnType == Short.TYPE) return Short.valueOf((short) 0);
        if (returnType == Integer.TYPE) return Integer.valueOf(0);
        if (returnType == Long.TYPE) return Long.valueOf(0L);
        if (returnType == Float.TYPE) return Float.valueOf(0F);
        if (returnType == Double.TYPE) return Double.valueOf(0D);
        return null;
    }

    private static Method findPreferenceListenerSetter(Class<?> type, int callbackArgumentCount) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (
                    method.getReturnType() == Void.TYPE &&
                    method.getParameterTypes().length == 1 &&
                    method.getParameterTypes()[0].isInterface() &&
                    hasBooleanCallback(method.getParameterTypes()[0], callbackArgumentCount, type, false)
                ) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean hasBooleanCallback(
        Class<?> listenerType,
        int argumentCount,
        Class<?> preferenceType,
        boolean allowObjectFirstParameter
    ) {
        return findBooleanCallback(
            listenerType,
            argumentCount,
            preferenceType,
            allowObjectFirstParameter
        ) != null;
    }

    private static Method findBooleanCallback(
        Class<?> listenerType,
        int argumentCount,
        Class<?> preferenceType,
        boolean allowObjectFirstParameter
    ) {
        Set<String> abstractSignatures = new HashSet<>();
        Method matchingCallback = null;
        for (Method callback : listenerType.getMethods()) {
            if (!Modifier.isAbstract(callback.getModifiers())) continue;
            Class<?>[] parameters = callback.getParameterTypes();
            if (isObjectMethod(callback)) continue;
            abstractSignatures.add(methodSignature(callback));
            if (callback.getReturnType() == Boolean.TYPE &&
                parameters.length == argumentCount &&
                (
                    allowObjectFirstParameter
                        ? parameters[0] == Object.class
                        : parameters[0] != Object.class && parameters[0].isAssignableFrom(preferenceType)
                )
            ) {
                matchingCallback = callback;
            }
        }
        return abstractSignatures.size() == 1 ? matchingCallback : null;
    }

    private static boolean isObjectMethod(Method method) {
        if ("equals".equals(method.getName())) {
            return method.getParameterTypes().length == 1 &&
                method.getParameterTypes()[0] == Object.class;
        }
        return ("hashCode".equals(method.getName()) || "toString".equals(method.getName())) &&
            method.getParameterTypes().length == 0;
    }

    private static String methodSignature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) {
            signature.append(parameter.getName()).append(';');
        }
        return signature.append(')').toString();
    }

    private static Field findPreferenceListenerField(
        Class<?> type,
        int callbackArgumentCount,
        boolean objectValueFieldFallback
    ) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                Class<?> listenerType = field.getType();
                if (!listenerType.isInterface()) continue;
                if (
                    objectValueFieldFallback &&
                    hasBooleanCallback(listenerType, 1, Object.class, true)
                ) return field;
                if (
                    !objectValueFieldFallback &&
                    hasBooleanCallback(listenerType, callbackArgumentCount, type, false)
                ) return field;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static void setSummary(Object preference, CharSequence summary) {
        if (preference == null) return;
        // 1. Try obfuscated method V(CharSequence)
        try {
            Method m = findMethod(preference.getClass(), "V", CharSequence.class);
            if (m != null) {
                m.invoke(preference, summary);
                return;
            }
        } catch (Throwable ignored) {}

        // 2. Try standard setSummary(CharSequence)
        try {
            Method m = findMethod(preference.getClass(), "setSummary", CharSequence.class);
            if (m != null) {
                m.invoke(preference, summary);
                return;
            }
        } catch (Throwable ignored) {}
    }

    public static SharedPreferences getPrefs(Context context) {
        try {
            Context deContext = getDeviceProtectedContext(context);
            if (deContext != null) {
                SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(deContext);
                if (p != null) return p;
            }
        } catch (Throwable ignored) {}
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void saveCustomColor(Context context, int color) {
        if (context == null) return;
        savePrefInt(context, PREF_CUSTOM_COLOR, color);
        if (color != 0) {
            // Setting a custom color disables rainbow mode so the chosen color is visible
            savePrefBoolean(context, PREF_RAINBOW, false);
        }
    }

    public static void savePrefInt(Context context, String key, int value) {
        if (context == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs != null) {
                prefs.edit()
                    .putInt(key, value)
                    .putBoolean(PREF_MASTER_ENABLED, true)
                    .commit();
            }
        } catch (Throwable ignored) {}

        try {
            Context deContext = getDeviceProtectedContext(context);
            if (deContext != null && deContext != context) {
                SharedPreferences dePrefs = PreferenceManager.getDefaultSharedPreferences(deContext);
                if (dePrefs != null) {
                    dePrefs.edit()
                        .putInt(key, value)
                        .putBoolean(PREF_MASTER_ENABLED, true)
                        .commit();
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void savePrefBoolean(Context context, String key, boolean value) {
        if (context == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs != null) {
                prefs.edit().putBoolean(key, value).commit();
            }
        } catch (Throwable ignored) {}

        try {
            Context deContext = getDeviceProtectedContext(context);
            if (deContext != null && deContext != context) {
                SharedPreferences dePrefs = PreferenceManager.getDefaultSharedPreferences(deContext);
                if (dePrefs != null) {
                    dePrefs.edit().putBoolean(key, value).commit();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Context getDeviceProtectedContext(Context context) {
        try {
            Method isDeMethod = context.getClass().getMethod("isDeviceProtectedStorage");
            Boolean isDe = (Boolean) isDeMethod.invoke(context);
            if (isDe != null && !isDe) {
                Method createDeMethod = context.getClass().getMethod("createDeviceProtectedStorageContext");
                return (Context) createDeMethod.invoke(context);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Field getField(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
