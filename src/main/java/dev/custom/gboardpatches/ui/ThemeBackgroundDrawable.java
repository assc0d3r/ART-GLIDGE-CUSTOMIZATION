package dev.custom.gboardpatches.ui;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

final class ThemeBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int baseColor;
    private final Bitmap image;
    private final int[] gradientColors;
    private final String gradientType;
    private final String animation;
    private final float gradientAngle;
    private final float animationSpeed;
    private final int imageOpacity;
    private final Matrix imageMatrix = new Matrix();
    private final BitmapShader imageShader;
    private final ValueAnimator animator;
    private float phase;
    private int alpha = 255;
    private ColorFilter colorFilter;

    ThemeBackgroundDrawable(
        int baseColor,
        Bitmap image,
        int[] gradientColors,
        String gradientType,
        float gradientAngle,
        String animation,
        float animationSpeed,
        int imageOpacity
    ) {
        this.baseColor = baseColor;
        this.image = image;
        this.gradientColors = gradientColors == null ? new int[0] : gradientColors.clone();
        this.gradientType = gradientType;
        this.gradientAngle = gradientAngle;
        this.animation = animation == null ? "NONE" : animation.toUpperCase(java.util.Locale.ROOT);
        this.animationSpeed = Math.max(0.25f, Math.min(3f, animationSpeed));
        this.imageOpacity = Math.max(0, Math.min(100, imageOpacity));
        imageShader = image == null ? null : new BitmapShader(
            image,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        );
        if ("FLOW".equals(this.animation) || "HUE_CYCLE".equals(this.animation)) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(Math.max(1000L, (long) (8000f / this.animationSpeed)));
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(value -> {
                phase = (Float) value.getAnimatedValue();
                invalidateSelf();
            });
            animator.start();
        } else {
            animator = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        RectF rect = new RectF(bounds);
        paint.setAlpha(alpha);
        paint.setColorFilter(colorFilter);
        paint.setColor("HUE_CYCLE".equals(animation) && gradientColors.length < 2
            ? rotateHue(baseColor, phase)
            : baseColor);
        canvas.drawRect(rect, paint);

        if (gradientColors.length >= 2) {
            int[] colors = gradientColors.clone();
            if ("HUE_CYCLE".equals(animation)) {
                for (int index = 0; index < colors.length; index++) {
                    colors[index] = rotateHue(colors[index], phase);
                }
            }
            float angle = gradientAngle + ("FLOW".equals(animation) ? phase * 360f : 0f);
            paint.setShader(gradient(rect, colors, gradientType, angle));
            canvas.drawRect(rect, paint);
            paint.setShader(null);
        }

        if (imageShader != null && !image.isRecycled() && imageOpacity > 0) {
            float scale = Math.max(rect.width() / image.getWidth(), rect.height() / image.getHeight());
            imageMatrix.setScale(scale, scale);
            imageMatrix.postTranslate(
                rect.centerX() - image.getWidth() * scale / 2f,
                rect.centerY() - image.getHeight() * scale / 2f
            );
            imageShader.setLocalMatrix(imageMatrix);
            paint.setShader(imageShader);
            paint.setAlpha(Math.round(alpha * imageOpacity / 100f));
            paint.setColorFilter(colorFilter);
            canvas.drawRect(rect, paint);
            paint.setShader(null);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        invalidateSelf();
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

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (animator != null) {
            if (visible && !animator.isStarted()) animator.start();
            else if (!visible && animator.isStarted()) animator.cancel();
        }
        return changed;
    }

    void stop() {
        if (animator != null) animator.cancel();
    }

    private Shader gradient(RectF rect, int[] colors, String kind, float angle) {
        String type = kind == null ? "LINEAR" : kind.toUpperCase(java.util.Locale.ROOT);
        if ("RADIAL".equals(type)) {
            float radius = Math.max(rect.width(), rect.height()) / 2f;
            return new RadialGradient(
                rect.centerX() + (float) Math.cos(phase * Math.PI * 2f) * radius * 0.25f,
                rect.centerY() + (float) Math.sin(phase * Math.PI * 2f) * radius * 0.25f,
                radius,
                colors,
                null,
                Shader.TileMode.CLAMP
            );
        }
        if ("SWEEP".equals(type)) {
            return new SweepGradient(rect.centerX(), rect.centerY(), colors, null);
        }
        double radians = Math.toRadians(angle);
        float dx = (float) Math.cos(radians) * rect.width() / 2f;
        float dy = (float) Math.sin(radians) * rect.height() / 2f;
        return new LinearGradient(
            rect.centerX() - dx,
            rect.centerY() - dy,
            rect.centerX() + dx,
            rect.centerY() + dy,
            colors,
            null,
            Shader.TileMode.CLAMP
        );
    }

    private int rotateHue(int color, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + amount * 360f) % 360f;
        return Color.HSVToColor(Color.alpha(color), hsv);
    }
}
