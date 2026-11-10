package dev.custom.gboardpatches.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ThemeStickerDrawable extends Drawable {
    interface BitmapLoader {
        Bitmap load(Map<String, Object> definition);
    }

    private static final class Sticker {
        final Bitmap bitmap;
        final float x;
        final float y;
        final float size;
        final float rotation;
        final int opacity;

        Sticker(Bitmap bitmap, float x, float y, float size, float rotation, int opacity) {
            this.bitmap = bitmap;
            this.x = x;
            this.y = y;
            this.size = size;
            this.rotation = rotation;
            this.opacity = Math.max(0, Math.min(100, opacity));
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final List<Sticker> stickers = new ArrayList<>();
    private int alpha = 255;
    private ColorFilter colorFilter;

    ThemeStickerDrawable(
        List<Object> definitions,
        BitmapLoader bitmapLoader
    ) {
        for (Object value : definitions) {
            if (!(value instanceof Map)) continue;
            Map<String, Object> definition = ThemeDocument.object(value);
            Bitmap bitmap = bitmapLoader.load(definition);
            if (bitmap == null || bitmap.isRecycled()) continue;
            stickers.add(new Sticker(
                bitmap,
                (float) ThemeDocument.decimal(definition, "x", 0.5),
                (float) ThemeDocument.decimal(definition, "y", 0.5),
                (float) ThemeDocument.decimal(definition, "size", 0.18),
                (float) ThemeDocument.decimal(definition, "rotation", 0),
                (int) ThemeDocument.number(definition, "opacity", 100)
            ));
        }
    }

    boolean isEmpty() {
        return stickers.isEmpty();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        for (Sticker sticker : stickers) {
            float dimension = Math.max(0.02f, Math.min(1f, sticker.size)) * bounds.width();
            float aspect = (float) sticker.bitmap.getHeight() / sticker.bitmap.getWidth();
            float width = dimension;
            float height = dimension * aspect;
            float centerX = bounds.left + Math.max(0f, Math.min(1f, sticker.x)) * bounds.width();
            float centerY = bounds.top + Math.max(0f, Math.min(1f, sticker.y)) * bounds.height();
            RectF destination = new RectF(
                centerX - width / 2f,
                centerY - height / 2f,
                centerX + width / 2f,
                centerY + height / 2f
            );
            int save = canvas.save();
            canvas.rotate(sticker.rotation, centerX, centerY);
            paint.setAlpha(Math.round(alpha * sticker.opacity / 100f));
            paint.setColorFilter(colorFilter);
            canvas.drawBitmap(sticker.bitmap, null, destination, paint);
            canvas.restoreToCount(save);
        }
    }

    @Override
    public void setAlpha(int value) {
        alpha = value;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        colorFilter = filter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
