package dev.custom.gboardpatches.ui;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.Base64;
import android.util.LruCache;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;

public final class KeyboardThemeRuntime {
    private static final String TAG = "GboardThemeStudio";
    private static volatile Map<String, Object> cachedTheme;
    private static volatile String cachedThemeId;
    private static volatile long cachedThemeLoadedAt;
    private static final WeakHashMap<View, ThemeBackgroundDrawable> BOARD_DRAWABLES = new WeakHashMap<>();
    private static final WeakHashMap<View, String> BOARD_THEME_IDS = new WeakHashMap<>();
    private static final WeakHashMap<View, ThemeStickerDrawable> STICKER_DRAWABLES = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> SURFACE_SCAN_TIMES = new WeakHashMap<>();
    private static final WeakHashMap<View, Drawable> ORIGINAL_BACKGROUNDS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, ColorStateList> ORIGINAL_TEXT_COLORS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, android.graphics.Typeface> ORIGINAL_TYPEFACES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_SIZES = new WeakHashMap<>();
    private static final LruCache<String, Bitmap> IMAGE_CACHE = new LruCache<String, Bitmap>(4096) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private KeyboardThemeRuntime() {}

    public static void initializePreferences(Object preferenceGroup) {
        if (preferenceGroup == null) return;
        List<Object> preferences = new java.util.ArrayList<>();
        collectPreferences(preferenceGroup, preferences);
        for (Object preference : preferences) {
            if (!KeyboardThemeStudioActivity.PREF_KEY_STUDIO.equals(GlideTrailPreferences.getKey(preference))) {
                continue;
            }
            GlideTrailPreferences.setPreferenceClickListener(preference, clickedPreference -> {
                Context context = GlideTrailPreferences.getContext(clickedPreference);
                if (context == null) {
                    Log.e(TAG, "Unable to open Theme Studio: preference context is unavailable.");
                    return true;
                }
                android.content.Intent intent = new android.content.Intent(
                    context,
                    KeyboardThemeStudioActivity.class
                );
                if (!(context instanceof android.app.Activity)) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                try {
                    context.startActivity(intent);
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Unable to open Gboard Theme Studio.", exception);
                }
                return true;
            });
        }
    }

    public static void applyKey(View keyView, Object softKeyDef) {
        if (keyView == null || softKeyDef == null) return;
        try {
            if ("builtin_default".equals(ThemeStore.activeId(keyView.getContext()))) {
                View root = keyView.getRootView();
                restoreViewStyles(keyView);
                if (root != null) {
                    applyBoard(root);
                    restoreSurfaceStyles(root);
                }
                return;
            }
            Map<String, Object> theme = theme(keyView.getContext());
            View root = keyView.getRootView();
            if (root != null) {
                applyBoard(root);
                applySurfaceStyles(root, theme);
            }
            String description = keyView.getContentDescription() == null
                ? String.valueOf(softKeyDef)
                : keyView.getContentDescription().toString();
            String lower = description.toLowerCase(java.util.Locale.ROOT);
            boolean enter = keyView.getClass().getName().contains("EnterSoftKeyView") ||
                lower.contains("enter") || lower.contains("return");
            boolean modifier = !enter && (lower.contains("shift") || lower.contains("delete") ||
                lower.contains("backspace") || lower.contains("symbol") || lower.contains("language") ||
                lower.contains("globe") || lower.contains("emoji") || lower.contains("voice"));

            String keySlot = KeyboardIconPackRuntime.resolveSlot(keyView, softKeyDef);
            Map<String, Object> keyStyles = ThemeDocument.object(theme.get("singleKeyStyles"));
            Map<String, Object> keyStyle = ThemeDocument.object(keyStyles.get(keySlot));
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
            if (keySlot != null &&
                (keySlot.startsWith("tool.") || keySlot.startsWith("chrome."))) {
                background = color(theme, "toolbarToolBackground", background);
                keyText = color(theme, "toolbarToolIcon", keyText);
            }
            background = color(keyStyle, "backgroundColor", background);
            keyText = color(keyStyle, "textColor", keyText);
            pressed = color(keyStyle, "pressedColor", pressed);
            border = color(keyStyle, "borderColor", border);
            shape = ThemeDocument.string(keyStyle, "keyShape", shape);
            radius = (float) ThemeDocument.number(keyStyle, "keyCornerRadiusDp",
                ThemeDocument.number(theme, "keyCornerRadiusDp", 6)) * density;
            borderWidth = (float) ThemeDocument.number(keyStyle, "borderWidthDp",
                ThemeDocument.number(theme, "keyBorderWidthDp", 0)) * density;
            Map<String, Object> gradient = ThemeDocument.object(theme.get("keyGradient"));
            int[] gradientColors = colors(gradient, background);
            String gradientType = ThemeDocument.string(gradient, "type", "LINEAR");
            float gradientAngle = (float) ThemeDocument.number(gradient, "angleDeg", 45);
            Bitmap texture = bitmap(theme, "keyTextureBase64");
            if (texture == null) texture = bitmap(keyStyle, "imageBase64");
            int textureOpacity = (int) ThemeDocument.number(theme, "keyTextureOpacity", 100);

            captureOriginalBackground(keyView);
            keyView.setBackground(new ThemeKeyDrawable(
                background,
                pressed,
                border,
                borderWidth,
                radius,
                shape,
                gradientColors,
                gradientType,
                gradientAngle,
                texture,
                ThemeDocument.string(theme, "keyTextureFit", "CROP"),
                textureOpacity
            ));
            setTextColors(keyView, keyText);
            applyKeyTextStyle(keyView, keyStyle);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply the selected Gboard theme to a key.", exception);
        }
    }

    public static void applyBoard(View boardView) {
        if (boardView == null) return;
        try {
            String id = ThemeStore.activeId(boardView.getContext());
            if (id.equals(BOARD_THEME_IDS.get(boardView))) {
                ThemeStickerDrawable existingSticker = STICKER_DRAWABLES.get(boardView);
                if (existingSticker != null) {
                    existingSticker.setBounds(0, 0, boardView.getWidth(), boardView.getHeight());
                }
                return;
            }
            ThemeBackgroundDrawable oldDrawable = BOARD_DRAWABLES.remove(boardView);
            if (oldDrawable != null) oldDrawable.stop();
            if ("builtin_default".equals(id)) {
                if (oldDrawable != null && ORIGINAL_BACKGROUNDS.containsKey(boardView)) {
                    boardView.setBackground(ORIGINAL_BACKGROUNDS.get(boardView));
                }
                SURFACE_SCAN_TIMES.remove(boardView);
                BOARD_THEME_IDS.put(boardView, id);
                removeStickers(boardView);
                return;
            }
            Map<String, Object> theme = theme(boardView.getContext());
            int color = color(theme, "boardBackground", 0xFF17181C);
            Object gradientValue = theme.get("boardGradient");
            int[] stops = new int[0];
            String type = "LINEAR";
            float angle = 45f;
            if (gradientValue instanceof Map) {
                Map<String, Object> gradient = ThemeDocument.object(gradientValue);
                stops = colors(gradient, color);
                type = ThemeDocument.string(gradient, "type", "LINEAR");
                angle = (float) ThemeDocument.number(gradient, "angleDeg", 45);
            }
            Bitmap image = bitmap(theme, "backgroundImageBase64");
            String animation = ThemeDocument.string(theme, "backgroundAnimation", "NONE");
            float speed = (float) ThemeDocument.decimal(theme, "backgroundAnimationSpeed", 1);
            int imageOpacity = (int) ThemeDocument.number(theme, "backgroundImageOpacity", 100);
            ThemeBackgroundDrawable drawable = new ThemeBackgroundDrawable(
                color,
                image,
                stops,
                type,
                angle,
                animation,
                speed,
                imageOpacity
            );
            captureOriginalBackground(boardView);
            boardView.setBackground(drawable);
            BOARD_DRAWABLES.put(boardView, drawable);
            BOARD_THEME_IDS.put(boardView, id);
            updateStickers(boardView, theme);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to apply the selected Gboard board theme.", exception);
        }
    }

    static void invalidate() {
        cachedTheme = null;
        cachedThemeId = null;
        cachedThemeLoadedAt = 0;
        clearBoardDrawables();
    }

    private static void clearBoardDrawables() {
        synchronized (BOARD_DRAWABLES) {
            for (ThemeBackgroundDrawable drawable : BOARD_DRAWABLES.values()) drawable.stop();
            BOARD_DRAWABLES.clear();
            BOARD_THEME_IDS.clear();
            for (Map.Entry<View, ThemeStickerDrawable> entry : STICKER_DRAWABLES.entrySet()) {
                removeOverlayDrawable(entry.getKey(), entry.getValue());
            }
            STICKER_DRAWABLES.clear();
            SURFACE_SCAN_TIMES.clear();
        }
    }

    static Bitmap previewBitmap(Map<String, Object> theme, String field) {
        return bitmap(theme, field);
    }

    private static Map<String, Object> theme(Context context) {
        String id = ThemeStore.activeId(context);
        Map<String, Object> current = cachedTheme;
        long now = SystemClock.elapsedRealtime();
        if (current != null && id.equals(cachedThemeId) &&
            (!Boolean.TRUE.equals(current.get("followWallpaperColors")) ||
                now - cachedThemeLoadedAt < 30_000)) return current;
        synchronized (KeyboardThemeRuntime.class) {
            boolean refreshWallpaper = cachedTheme != null && id.equals(cachedThemeId) &&
                Boolean.TRUE.equals(cachedTheme.get("followWallpaperColors")) &&
                now - cachedThemeLoadedAt >= 30_000;
            if (cachedTheme == null || !id.equals(cachedThemeId) || refreshWallpaper) {
                cachedTheme = ThemeStore.active(context);
                applyWallpaperColors(context, cachedTheme);
                cachedThemeId = id;
                cachedThemeLoadedAt = now;
                if (refreshWallpaper) clearBoardDrawables();
            }
            return cachedTheme;
        }
    }

    private static void applyWallpaperColors(Context context, Map<String, Object> theme) {
        if (!Boolean.TRUE.equals(theme.get("followWallpaperColors")) || Build.VERSION.SDK_INT < 27) return;
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            Object palette = manager.getClass().getMethod("getWallpaperColors", int.class)
                .invoke(manager, 1);
            if (palette == null) return;
            Object primaryColor = palette.getClass().getMethod("getPrimaryColor").invoke(palette);
            if (primaryColor == null) return;
            int primary = ((Number) primaryColor.getClass().getMethod("toArgb").invoke(primaryColor)).intValue();
            int tertiary = primary;
            try {
                Object tertiaryColor = palette.getClass().getMethod("getTertiaryColor").invoke(palette);
                if (tertiaryColor != null) {
                    tertiary = ((Number) tertiaryColor.getClass().getMethod("toArgb")
                        .invoke(tertiaryColor)).intValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Older Android releases may expose only the primary wallpaper color.
            }
            float[] primaryHsv = new float[3];
            Color.colorToHSV(primary, primaryHsv);
            float[] tertiaryHsv = new float[3];
            Color.colorToHSV(tertiary, tertiaryHsv);
            String[] colorFields = {
                "boardBackground", "keyBackground", "modifierKeyBackground", "enterKeyBackground",
                "pressedKeyBackground", "accent", "keyBorderColor", "toolbarToolBackground",
                "toolbarToolIcon", "popupBackground", "popupText", "popupOutline"
            };
            for (String field : colorFields) {
                if (!theme.containsKey(field)) continue;
                int original = color(theme, field, Color.TRANSPARENT);
                float[] hsv = new float[3];
                Color.colorToHSV(original, hsv);
                if (hsv[1] < 0.08f) continue;
                float hueDistance = Math.abs(hsv[0] - primaryHsv[0]);
                hueDistance = Math.min(hueDistance, 360f - hueDistance);
                float[] source = hueDistance > 140f ? tertiaryHsv : primaryHsv;
                hsv[0] = source[0];
                hsv[1] = Math.min(hsv[1], Math.max(0.15f, source[1]));
                theme.put(field, (long) Color.HSVToColor(Color.alpha(original), hsv));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Unable to apply wallpaper colors to the active theme.", exception);
        }
    }

    private static int color(Map<String, Object> theme, String key, int fallback) {
        return ThemeDocument.color(theme, key, fallback);
    }

    private static Bitmap bitmap(Map<String, Object> source, String field) {
        String encoded = ThemeDocument.string(source, field, "");
        if (encoded.isEmpty()) return null;
        String cacheKey = field + ":" + encoded.hashCode() + ":" + encoded.length();
        Bitmap cached = IMAGE_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) return cached;
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth / options.inSampleSize,
                bounds.outHeight / options.inSampleSize) > 1024) {
                options.inSampleSize *= 2;
            }

            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (decoded != null) IMAGE_CACHE.put(cacheKey, decoded);
            return decoded;
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "A stored theme image is not valid Base64.", exception);
            return null;
        }
    }

    private static void updateStickers(View host, Map<String, Object> theme) {
        List<Object> definitions = ThemeDocument.array(theme.get("stickers"));
        if (definitions.isEmpty()) {
            removeStickers(host);
            return;
        }
        ThemeStickerDrawable sticker = new ThemeStickerDrawable(
            definitions,
            definition -> bitmap(definition, "imageBase64")
        );
        if (sticker.isEmpty()) return;
        removeStickers(host);
        sticker.setBounds(0, 0, host.getWidth(), host.getHeight());
        if (addOverlayDrawable(host, sticker)) STICKER_DRAWABLES.put(host, sticker);
    }

    private static void removeStickers(View host) {
        ThemeStickerDrawable old = STICKER_DRAWABLES.remove(host);
        if (old != null) removeOverlayDrawable(host, old);
    }

    private static boolean addOverlayDrawable(View host, Drawable drawable) {
        try {
            Object overlay = host.getClass().getMethod("getOverlay").invoke(host);
            overlay.getClass().getMethod("add", Drawable.class).invoke(overlay, drawable);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "Gboard's keyboard view overlay is unavailable for theme stickers.", exception);
            return false;
        }
    }

    private static void removeOverlayDrawable(View host, Drawable drawable) {
        try {
            Object overlay = host.getClass().getMethod("getOverlay").invoke(host);
            overlay.getClass().getMethod("remove", Drawable.class).invoke(overlay, drawable);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w(TAG, "A theme sticker overlay could not be removed.", exception);
        }
    }

    private static int[] colors(Map<String, Object> gradient, int fallback) {
        List<Object> values = ThemeDocument.array(gradient.get("colors"));
        if (values.size() < 2 || values.size() > 12) return new int[0];
        int[] result = new int[values.size()];
        for (int index = 0; index < result.length; index++) {
            Map<String, Object> colorValue = new java.util.HashMap<>();
            colorValue.put("value", values.get(index));
            result[index] = ThemeDocument.color(colorValue, "value", fallback);
        }
        return result;
    }

    private static int blend(int first, int second) {
        return android.graphics.Color.argb(
            (android.graphics.Color.alpha(first) + android.graphics.Color.alpha(second)) / 2,
            (android.graphics.Color.red(first) + android.graphics.Color.red(second)) / 2,
            (android.graphics.Color.green(first) + android.graphics.Color.green(second)) / 2,
            (android.graphics.Color.blue(first) + android.graphics.Color.blue(second)) / 2
        );
    }

    private static void setTextColors(View view, int color) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (!ORIGINAL_TEXT_COLORS.containsKey(textView)) {
                ORIGINAL_TEXT_COLORS.put(textView, textView.getTextColors());
            }
            textView.setTextColor(color);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setTextColors(group.getChildAt(index), color);
        }
    }

    private static void applySurfaceStyles(View root, Map<String, Object> theme) {
        long now = SystemClock.uptimeMillis();
        Long previous = SURFACE_SCAN_TIMES.get(root);
        if (previous != null && now - previous < 500) return;
        SURFACE_SCAN_TIMES.put(root, now);
        styleSurfaceTree(root, theme);
    }

    private static void restoreSurfaceStyles(View root) {
        long now = SystemClock.uptimeMillis();
        Long previous = SURFACE_SCAN_TIMES.get(root);
        if (previous != null && now - previous < 500) return;
        SURFACE_SCAN_TIMES.put(root, now);
        restoreViewStyles(root);
    }

    private static void styleSurfaceTree(View view, Map<String, Object> theme) {
        String identity = view.getClass().getName().toLowerCase(java.util.Locale.ROOT)
            + " " + resourceName(view).toLowerCase(java.util.Locale.ROOT);
        if (containsAny(identity, "popup", "longpress", "key_preview", "alternate_key")) {
            setSurfaceBackground(view, theme, "popupBackground", "popupText",
                ThemeDocument.string(theme, "popupShape", "ROUNDED"),
                ThemeDocument.number(theme, "popupCornerRadiusDp", 12),
                "popupOutline", "popupOutlineWidthDp");
        } else if (containsAny(identity, "suggestion", "candidate", "prediction")) {
            setSurfaceBackground(view, theme, "suggestionStripBackground", "suggestionText",
                "ROUNDED", 0, "keyBorderColor", "keyBorderWidthDp");
        } else if (containsAny(identity, "toolbar", "tool_bar", "quick_tools")) {
            setSurfaceBackground(view, theme, "toolbarBackground", "toolbarToolIcon",
                "ROUNDED", 0, "keyBorderColor", "keyBorderWidthDp");
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            styleSurfaceTree(group.getChildAt(index), theme);
        }
    }

    private static void applyKeyTextStyle(View view, Map<String, Object> style) {
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            if (!ORIGINAL_TYPEFACES.containsKey(label)) {
                ORIGINAL_TYPEFACES.put(label, label.getTypeface());
                ORIGINAL_TEXT_SIZES.put(label, label.getTextSize());
            }
            Object boldValue = style.get("boldLabel");
            if (boldValue instanceof Boolean) {
                android.graphics.Typeface original = ORIGINAL_TYPEFACES.get(label);
                label.setTypeface(original,
                    (Boolean) boldValue ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            } else {
                label.setTypeface(ORIGINAL_TYPEFACES.get(label));
            }
            long size = ThemeDocument.number(style, "labelSizeSp", 0);
            if (size > 0) label.setTextSize((float) size);
            else {
                Float originalSize = ORIGINAL_TEXT_SIZES.get(label);
                if (originalSize != null) label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize);
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            applyKeyTextStyle(group.getChildAt(index), style);
        }
    }

    private static void setSurfaceBackground(
        View view,
        Map<String, Object> theme,
        String backgroundField,
        String textField,
        String shape,
        long radius,
        String borderField,
        String borderWidthField
    ) {
        int background = color(theme, backgroundField, 0xFF17181C);
        int text = color(theme, textField, 0xFFE9E9EE);
        float density = view.getResources().getDisplayMetrics().density;
        long cornerRadius = "popupBackground".equals(backgroundField)
            ? ThemeDocument.number(theme, "popupCornerRadiusDp", radius)
            : radius;
        captureOriginalBackground(view);
        view.setBackground(new ThemeKeyDrawable(
            background,
            background,
            color(theme, borderField, 0),
            (float) ThemeDocument.number(theme, borderWidthField, 0) * density,
            (float) cornerRadius * density,
            shape,
            new int[0],
            "LINEAR",
            45,
            null,
            "CROP",
            0
        ));
        setTextColors(view, text);
    }

    private static void captureOriginalBackground(View view) {
        if (!ORIGINAL_BACKGROUNDS.containsKey(view)) {
            ORIGINAL_BACKGROUNDS.put(view, view.getBackground());
        }
    }

    private static void restoreViewStyles(View view) {
        if (ORIGINAL_BACKGROUNDS.containsKey(view)) {
            view.setBackground(ORIGINAL_BACKGROUNDS.get(view));
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            ColorStateList originalColors = ORIGINAL_TEXT_COLORS.get(textView);
            if (originalColors != null) textView.setTextColor(originalColors);
            android.graphics.Typeface originalTypeface = ORIGINAL_TYPEFACES.get(textView);
            if (originalTypeface != null) textView.setTypeface(originalTypeface);
            Float originalSize = ORIGINAL_TEXT_SIZES.get(textView);
            if (originalSize != null) {
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize);
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            restoreViewStyles(group.getChildAt(index));
        }
    }

    private static String resourceName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
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
