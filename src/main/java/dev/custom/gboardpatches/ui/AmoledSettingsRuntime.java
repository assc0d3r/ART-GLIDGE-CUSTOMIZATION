package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class AmoledSettingsRuntime {
    private static final String TAG = "GboardAmoledSettings";
    private static final int BLACK = Color.BLACK;
    private static final int PRIMARY_TEXT = 0xFFF5F5F5;
    private static final int SECONDARY_TEXT = 0xFFBDBDBD;
    private static final int ACCENT = 0xFF80CBC4;
    private static final int LIGHT_STATUS_BAR = 0x00002000;
    private static final int LIGHT_NAVIGATION_BAR = 0x00000010;
    private static final Set<View> STYLED_ROWS =
        Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<View> STYLED_ROOTS =
        Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private AmoledSettingsRuntime() {}

    public static void applyPreference(Object preference, Object holder) {
        if (holder == null) return;
        try {
            View row = preferenceView(holder);
            if (row == null) {
                Log.w(TAG, "Preference holder did not expose its item view.");
                return;
            }
            if (STYLED_ROWS.add(row)) {
                styleTree(row);
                View root = styleParentChain(row);
                if (STYLED_ROOTS.add(root)) styleTree(root);
                styleWindow(row.getContext());
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply AMOLED styling to a Gboard settings row.", exception);
        }
    }

    private static View preferenceView(Object holder) {
        try {
            Method accessor = findMethod(holder.getClass(), "getItemView");
            if (accessor != null) {
                Object value = accessor.invoke(holder);
                if (value instanceof View) return (View) value;
            }
            Field field = findField(holder.getClass(), "itemView");
            if (field == null) field = findField(holder.getClass(), "a");
            if (field == null) return null;
            field.setAccessible(true);
            Object value = field.get(holder);
            return value instanceof View ? (View) value : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Unable to read the AndroidX preference item view.", exception);
            return null;
        }
    }

    private static void styleTree(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int id = text.getId();
            text.setTextColor(id == android.R.id.summary ? SECONDARY_TEXT : PRIMARY_TEXT);
            text.setHintTextColor(SECONDARY_TEXT);
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            image.setColorFilter(PRIMARY_TEXT, PorterDuff.Mode.SRC_IN);
        }
        if (view instanceof CompoundButton) {
            setTint(view, "setButtonTintList", ACCENT);
            setTint(view, "setThumbTintList", ACCENT);
            setTint(view, "setTrackTintList", 0xFF526D68);
        }
        if (view instanceof ProgressBar) {
            setTint(view, "setProgressTintList", ACCENT);
            setTint(view, "setProgressBackgroundTintList", 0xFF303030);
            setTint(view, "setIndeterminateTintList", ACCENT);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            styleTree(group.getChildAt(index));
        }
    }

    private static View styleParentChain(View row) {
        View current = row;
        View root = row;
        int depth = 0;
        while (current != null && depth++ < 12) {
            current.setBackgroundColor(BLACK);
            root = current;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return root;
    }

    private static void styleWindow(Context context) {
        Activity activity = findActivity(context);
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            invokeColor(window, "setStatusBarColor", BLACK);
            invokeColor(window, "setNavigationBarColor", BLACK);
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility() & ~(LIGHT_STATUS_BAR | LIGHT_NAVIGATION_BAR);
            decor.setSystemUiVisibility(flags);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply AMOLED styling to the Gboard settings window.", exception);
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static void setTint(View view, String methodName, int color) {
        try {
            Method method = findMethod(view.getClass(), methodName, ColorStateList.class);
            if (method != null) method.invoke(view, ColorStateList.valueOf(color));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Unable to set AMOLED control tint using " + methodName + ".", exception);
        }
    }

    private static void invokeColor(Object target, String methodName, int color) {
        try {
            Method method = findMethod(target.getClass(), methodName, int.class);
            if (method != null) method.invoke(target, color);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Unable to set AMOLED window color using " + methodName + ".", exception);
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
}
