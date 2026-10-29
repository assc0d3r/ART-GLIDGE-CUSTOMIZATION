package dev.custom.gboardpatches.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class ThemeKeyDrawable extends Drawable {
    private static final int[] PRESSED_STATE = { android.R.attr.state_pressed };

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int normalColor;
    private final int pressedColor;
    private final int borderColor;
    private final float borderWidth;
    private final float radius;
    private final String shape;
    private boolean pressed;
    private int alpha = 255;
    private ColorFilter colorFilter;

    ThemeKeyDrawable(
        int normalColor,
        int pressedColor,
        int borderColor,
        float borderWidth,
        float radius,
        String shape
    ) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        this.radius = radius;
        this.shape = shape;
        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float halfBorder = borderWidth / 2f;
        RectF rect = new RectF(bounds.left + halfBorder, bounds.top + halfBorder,
            bounds.right - halfBorder, bounds.bottom - halfBorder);
        buildPath(rect);
        fillPaint.setColor(pressed ? pressedColor : normalColor);
        fillPaint.setAlpha(alpha);
        fillPaint.setColorFilter(colorFilter);
        canvas.drawPath(path, fillPaint);
        if (borderWidth > 0f) {
            borderPaint.setColor(borderColor);
            borderPaint.setAlpha(alpha);
            borderPaint.setColorFilter(colorFilter);
            canvas.drawPath(path, borderPaint);
        }
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean nextPressed = android.util.StateSet.stateSetMatches(PRESSED_STATE, state);
        if (pressed == nextPressed) return false;
        pressed = nextPressed;
        invalidateSelf();
        return true;
    }

    @Override
    public boolean isStateful() {
        return pressedColor != normalColor;
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

    private void buildPath(RectF rect) {
        path.reset();
        String kind = shape == null ? "ROUNDED" : shape.toUpperCase(java.util.Locale.ROOT);
        float width = rect.width();
        float height = rect.height();
        float shortSide = Math.min(width, height);
        float corner = Math.max(0f, Math.min(radius, shortSide / 2f));

        if ("SHARP".equals(kind) || "SQUARE".equals(kind)) {
            path.addRect(rect, Path.Direction.CW);
        } else if ("PILL".equals(kind)) {
            path.addRoundRect(rect, shortSide / 2f, shortSide / 2f, Path.Direction.CW);
        } else if ("CIRCLE".equals(kind) && width <= height * 1.2f) {
            float diameter = Math.min(width, height);
            RectF circle = new RectF(
                rect.centerX() - diameter / 2f,
                rect.centerY() - diameter / 2f,
                rect.centerX() + diameter / 2f,
                rect.centerY() + diameter / 2f
            );
            path.addOval(circle, Path.Direction.CW);
        } else if ("HEXAGON".equals(kind) || "SLANT".equals(kind) || "CUT".equals(kind)) {
            float cut = "HEXAGON".equals(kind) ? width / 6f : "SLANT".equals(kind) ? height / 3f : corner;
            path.moveTo(rect.left + cut, rect.top);
            path.lineTo(rect.right - cut, rect.top);
            path.lineTo(rect.right, rect.top + cut);
            path.lineTo(rect.right, rect.bottom - cut);
            path.lineTo(rect.right - cut, rect.bottom);
            path.lineTo(rect.left + cut, rect.bottom);
            path.lineTo(rect.left, rect.bottom - cut);
            path.lineTo(rect.left, rect.top + cut);
            path.close();
        } else if ("ARCH".equals(kind)) {
            path.moveTo(rect.left, rect.bottom);
            path.lineTo(rect.left, rect.top + shortSide / 2f);
            path.quadTo(rect.centerX(), rect.top - shortSide / 2f, rect.right, rect.top + shortSide / 2f);
            path.lineTo(rect.right, rect.bottom);
            path.close();
        } else if ("LEAF".equals(kind)) {
            path.moveTo(rect.left + corner, rect.top);
            path.lineTo(rect.right, rect.top);
            path.lineTo(rect.right, rect.bottom - corner);
            path.quadTo(rect.centerX(), rect.bottom, rect.left, rect.top + corner);
            path.quadTo(rect.left, rect.top, rect.left + corner, rect.top);
            path.close();
        } else if ("TICKET".equals(kind)) {
            float bite = Math.min(shortSide / 6f, corner);
            path.moveTo(rect.left + bite, rect.top);
            path.lineTo(rect.right - bite, rect.top);
            path.lineTo(rect.right, rect.top + bite);
            path.lineTo(rect.right, rect.bottom - bite);
            path.lineTo(rect.right - bite, rect.bottom);
            path.lineTo(rect.left + bite, rect.bottom);
            path.lineTo(rect.left, rect.bottom - bite);
            path.lineTo(rect.left, rect.top + bite);
            path.close();
        } else {
            float curve = "SQUIRCLE".equals(kind) ? corner * 1.25f : corner;
            path.addRoundRect(rect, curve, curve, Path.Direction.CW);
        }
    }
}
