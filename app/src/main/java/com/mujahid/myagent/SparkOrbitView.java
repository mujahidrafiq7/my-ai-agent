package com.mujahid.myagent;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Header me AYESHA text ke ird-gird ghoomti bijli-jaisi sparks.
 * Koi box nahi — sirf halki blue light letters ke bahar slow ghoomti hai.
 */
public class SparkOrbitView extends View {

    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0f;
    private ValueAnimator animator;

    // brightness pattern — bijli jaisi jhalak
    private static final int[] BRIGHT = {255, 110, 210, 90, 255, 130, 230, 80, 190};

    public SparkOrbitView(Context c, AttributeSet a) {
        super(c, a);
        sparkPaint.setStyle(Paint.Style.STROKE);
        sparkPaint.setStrokeCap(Paint.Cap.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(12000); // slow — halki halki
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

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float rx = getWidth() * 0.34f;
        float ry = getHeight() * 0.36f;
        if (rx <= 0 || ry <= 0) return;

        float base = progress * 360f;
        for (int i = 0; i < BRIGHT.length; i++) {
            double a = Math.toRadians(base + i * 40 + 8);
            int b = BRIGHT[i];
            sparkPaint.setColor(Color.rgb(0, Math.min(255, b), 255));
            sparkPaint.setStrokeWidth(b > 200 ? 5f : 3f);
            float x = cx + rx * (float) Math.cos(a);
            float y = cy + ry * (float) Math.sin(a);
            double a2 = Math.toRadians(base + i * 40 + 8 + 17);
            float x2 = cx + rx * (float) Math.cos(a2);
            float y2 = cy + ry * (float) Math.sin(a2);
            canvas.drawLine(x, y, x2, y2, sparkPaint);
            if (b > 200) {
                dotPaint.setColor(Color.rgb(225, 252, 255));
                canvas.drawCircle(x, y, 3.5f, dotPaint);
            }
        }
    }
}
