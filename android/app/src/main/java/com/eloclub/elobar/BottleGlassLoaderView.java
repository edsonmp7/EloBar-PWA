package com.eloclub.elobar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Marca dinâmica do loading do Elo Bar.
 * Garrafa + copo baixo de uísque desenhados nativamente, sem bitmap adicional.
 * O nível do líquido acompanha a porcentagem real informada pela Activity.
 */
public final class BottleGlassLoaderView extends View {
    private static final int GOLD = Color.rgb(215, 167, 43);
    private static final int GOLD_LIGHT = Color.rgb(255, 220, 126);
    private static final int GOLD_DARK = Color.rgb(122, 82, 20);

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint liquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bottlePath = new Path();
    private final Path glassPath = new Path();
    private final Path liquidPath = new Path();
    private final Path streamPath = new Path();

    private ValueAnimator animator;
    private float phase;
    private float progressFraction = 0.05f;
    private boolean completed;

    public BottleGlassLoaderView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.65f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(GOLD);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(4.8f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setColor(Color.argb(42, 232, 178, 55));

        liquidPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(1.0f));
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setColor(Color.argb(190, 255, 225, 145));
    }

    public void setProgress(int percent) {
        float next = Math.max(0f, Math.min(1f, percent / 100f));
        if (next < progressFraction && percent > 5) return;
        progressFraction = next;
        completed = percent >= 100;
        invalidate();
    }

    public void complete() {
        progressFraction = 1f;
        completed = true;
        invalidate();
        postDelayed(this::stopAnimation, 420L);
    }

    public void startAnimation() {
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1700L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pulse = completed ? 1f : 0.88f + 0.12f * (float) Math.sin(phase * Math.PI * 2.0);
        linePaint.setAlpha(Math.round(205 + 50 * pulse));
        glowPaint.setAlpha(Math.round(22 + 24 * pulse));

        drawBottle(canvas, w, h);
        drawStream(canvas, w, h);
        drawGlass(canvas, w, h);
        drawGlints(canvas, w, h);
    }

    private void drawBottle(Canvas canvas, float w, float h) {
        float cx = w * 0.34f;
        float cy = h * 0.35f;
        canvas.save();
        canvas.rotate(-29f, cx, cy);

        float left = w * 0.20f;
        float right = w * 0.43f;
        float top = h * 0.10f;
        float neckTop = h * 0.035f;
        float bottom = h * 0.60f;

        bottlePath.reset();
        bottlePath.moveTo(w * 0.285f, neckTop);
        bottlePath.lineTo(w * 0.365f, neckTop);
        bottlePath.lineTo(w * 0.370f, h * 0.16f);
        bottlePath.cubicTo(w * 0.374f, h * 0.20f, right, h * 0.23f, right, h * 0.30f);
        bottlePath.lineTo(right, bottom - h * 0.06f);
        bottlePath.quadTo(right, bottom, right - w * 0.055f, bottom);
        bottlePath.lineTo(left + w * 0.055f, bottom);
        bottlePath.quadTo(left, bottom, left, bottom - h * 0.06f);
        bottlePath.lineTo(left, h * 0.30f);
        bottlePath.cubicTo(left, h * 0.23f, w * 0.276f, h * 0.20f, w * 0.280f, h * 0.16f);
        bottlePath.close();

        canvas.drawPath(bottlePath, glowPaint);
        canvas.drawPath(bottlePath, linePaint);

        linePaint.setAlpha(175);
        canvas.drawLine(w * 0.287f, top, w * 0.367f, top, linePaint);
        canvas.drawLine(w * 0.292f, h * 0.145f, w * 0.365f, h * 0.145f, linePaint);
        canvas.drawLine(w * 0.235f, h * 0.34f, w * 0.235f, h * 0.51f, highlightPaint);
        linePaint.setAlpha(255);
        canvas.restore();
    }

    private void drawStream(Canvas canvas, float w, float h) {
        if (completed) return;

        float startX = w * 0.465f;
        float startY = h * 0.31f;
        float endX = w * 0.615f;
        float endY = h * 0.58f;
        float sway = dp(2.3f) * (float) Math.sin(phase * Math.PI * 2.0);

        streamPath.reset();
        streamPath.moveTo(startX, startY);
        streamPath.cubicTo(w * 0.50f + sway, h * 0.39f, w * 0.56f - sway, h * 0.49f, endX, endY);

        glowPaint.setStrokeWidth(dp(5.2f));
        canvas.drawPath(streamPath, glowPaint);
        glowPaint.setStrokeWidth(dp(4.8f));

        linePaint.setStrokeWidth(dp(2.0f));
        linePaint.setShader(new LinearGradient(startX, startY, endX, endY, GOLD_LIGHT, GOLD, Shader.TileMode.CLAMP));
        canvas.drawPath(streamPath, linePaint);
        linePaint.setShader(null);
        linePaint.setStrokeWidth(dp(1.65f));

        float travel = (phase * 1.35f) % 1f;
        float dropX = startX + (endX - startX) * travel + sway * (1f - travel);
        float dropY = startY + (endY - startY) * travel;
        liquidPaint.setColor(Color.argb(220, 244, 190, 59));
        canvas.drawCircle(dropX, dropY, dp(1.45f), liquidPaint);

        float drop2 = (phase * 1.35f + 0.47f) % 1f;
        float drop2X = startX + (endX - startX) * drop2 - sway * 0.45f;
        float drop2Y = startY + (endY - startY) * drop2;
        canvas.drawCircle(drop2X, drop2Y, dp(0.9f), liquidPaint);
    }

    private void drawGlass(Canvas canvas, float w, float h) {
        float topY = h * 0.56f;
        float bottomY = h * 0.90f;
        float leftTop = w * 0.48f;
        float rightTop = w * 0.80f;
        float leftBottom = w * 0.515f;
        float rightBottom = w * 0.765f;

        glassPath.reset();
        glassPath.moveTo(leftTop, topY);
        glassPath.lineTo(rightTop, topY);
        glassPath.lineTo(rightBottom, bottomY);
        glassPath.quadTo(w * 0.64f, h * 0.94f, leftBottom, bottomY);
        glassPath.close();

        canvas.drawPath(glassPath, glowPaint);
        canvas.drawPath(glassPath, linePaint);

        float innerTop = topY + dp(4f);
        float innerBottom = bottomY - dp(4f);
        float fillHeight = (innerBottom - innerTop) * Math.max(0.06f, progressFraction);
        float fillTop = innerBottom - fillHeight;
        float wave = dp(1.8f) * (completed ? 0.25f : (float) Math.sin(phase * Math.PI * 2.0));

        liquidPath.reset();
        liquidPath.moveTo(leftTop + dp(5f), fillTop);
        liquidPath.cubicTo(w * 0.56f, fillTop - wave, w * 0.69f, fillTop + wave, rightTop - dp(5f), fillTop);
        liquidPath.lineTo(rightBottom - dp(4f), innerBottom);
        liquidPath.quadTo(w * 0.64f, innerBottom + dp(2f), leftBottom + dp(4f), innerBottom);
        liquidPath.close();

        canvas.save();
        canvas.clipPath(glassPath);
        liquidPaint.setShader(new LinearGradient(0f, fillTop, 0f, innerBottom, GOLD_LIGHT, GOLD_DARK, Shader.TileMode.CLAMP));
        liquidPaint.setAlpha(205);
        canvas.drawPath(liquidPath, liquidPaint);
        liquidPaint.setShader(null);
        canvas.restore();

        highlightPaint.setAlpha(completed ? 245 : 205);
        Path surface = new Path();
        surface.moveTo(leftTop + dp(6f), fillTop);
        surface.cubicTo(w * 0.57f, fillTop - wave, w * 0.70f, fillTop + wave, rightTop - dp(6f), fillTop);
        canvas.drawPath(surface, highlightPaint);

        liquidPaint.setShader(new LinearGradient(
                w * 0.50f, 0f, w * 0.78f, 0f,
                Color.TRANSPARENT,
                Color.argb(completed ? 150 : 75, 255, 213, 107),
                Shader.TileMode.MIRROR
        ));
        RectF reflection = new RectF(w * 0.51f, h * 0.925f, w * 0.77f, h * 0.965f);
        canvas.drawOval(reflection, liquidPaint);
        liquidPaint.setShader(null);
    }

    private void drawGlints(Canvas canvas, float w, float h) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
        int alpha = completed ? 210 : Math.round(80 + 90 * pulse);
        liquidPaint.setColor(Color.argb(alpha, 255, 221, 125));

        canvas.drawCircle(w * 0.83f, h * 0.63f, dp(completed ? 1.6f : 1.0f), liquidPaint);
        canvas.drawCircle(w * 0.44f, h * 0.48f, dp(0.75f), liquidPaint);
        if (completed) {
            canvas.drawCircle(w * 0.82f, h * 0.48f, dp(1.0f), liquidPaint);
            canvas.drawCircle(w * 0.46f, h * 0.68f, dp(0.85f), liquidPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
