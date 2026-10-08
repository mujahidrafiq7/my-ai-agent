package com.mujahid.myagent;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Photorealistic reactor — teeno layers alag alag ghoomti hain:
 * outer arcs clockwise, tick ring anti-clockwise, core saans leta hai.
 * Bitmaps black background pe hain → SCREEN blend se kala ghaib, sirf glow rehta hai.
 * Tap = conversation ON/OFF (MainActivity handle karta hai).
 */
public class ReactorView extends View {

    private Bitmap outerBmp, ticksBmp, coreBmp;
    private boolean useBitmaps = false;
    private final Paint screenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // purana vector fallback
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float progress = 0f;
    private boolean active = false;
    private ValueAnimator animator;

    public ReactorView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        setFocusable(true);
        screenPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        try {
            outerBmp = BitmapFactory.decodeResource(getResources(), R.drawable.reactor_outer);
            ticksBmp = BitmapFactory.decodeResource(getResources(), R.drawable.reactor_ticks);
            coreBmp = BitmapFactory.decodeResource(getResources(), R.drawable.reactor_core);
            useBitmaps = outerBmp != null && ticksBmp != null && coreBmp != null;
        } catch (Exception ignored) {
            useBitmaps = false;
        }
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(18f);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(Color.rgb(0, 229, 255));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(9000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    public void setActive(boolean on) {
        active = on;
        if (animator != null) animator.setDuration(on ? 3500 : 9000);
    }

    private void drawSpinning(Bitmap bmp, Canvas canvas, float cx, float cy,
                              float size, float degrees, int alpha) {
        float half = size / 2f;
        RectF dst = new RectF(cx - half, cy - half, cx + half, cy + half);
        screenPaint.setAlpha(alpha);
        canvas.save();
        canvas.rotate(degrees, cx, cy);
        canvas.drawBitmap(bmp, null, dst, screenPaint);
        canvas.restore();
        screenPaint.setAlpha(255);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        if (!useBitmaps) {
            drawVectorFallback(canvas, cx, cy, size);
            return;
        }

        float speed = active ? 2.0f : 1f;
        float base = progress * 360f * speed;

        // 1) outer arcs — clockwise
        drawSpinning(outerBmp, canvas, cx, cy, size, base, 255);
        // 2) tick ring — anti-clockwise, thodi chhoti
        drawSpinning(ticksBmp, canvas, cx, cy, size * 0.80f, -base * 0.7f, 235);
        // 3) core — ghoomta nahi, saans leta hai
        float pulse = 0.72f + 0.28f * (float) Math.sin(progress * Math.PI * 4);
        float coreSize = size * 0.62f * (0.96f + 0.06f * pulse);
        RectF dst = new RectF(cx - coreSize / 2f, cy - coreSize / 2f,
                cx + coreSize / 2f, cy + coreSize / 2f);
        corePaint.setAlpha((int) (200 + 55 * pulse));
        corePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        canvas.drawBitmap(coreBmp, null, dst, corePaint);
    }

    /** Bitmaps na milen to purana vector design (kabhi khali screen na ho). */
    private void drawVectorFallback(Canvas canvas, float cx, float cy, float size) {
        float R = size / 2f - 24f;
        if (R <= 0) return;
        float base = progress * 360f;
        RectF outer = new RectF(cx - R, cy - R, cx + R, cy + R);
        for (int s : new int[]{15, 135, 255}) {
            canvas.drawArc(outer, base + s, 70f, false, arcPaint);
        }
        corePaint.setColor(Color.rgb(0, 229, 255));
        corePaint.setXfermode(null);
        canvas.drawCircle(cx, cy, 26f, corePaint);
        corePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 14f, corePaint);
    }
}
