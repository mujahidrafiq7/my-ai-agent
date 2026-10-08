package com.mujahid.myagent;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * MYRA video jaisa rainbow neon border — poori home screen ke gird.
 * Rang dheere dheere badalte hain (hue 0→360, ~10 sec me poora cycle),
 * non-stop loop. Neon tube jaisa: bahar soft glow + andar tez core line.
 */
public class NeonBorderView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] hsv = {0f, 1f, 1f};
    private ValueAnimator anim;

    public NeonBorderView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur ke liye zaroori
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(22f);
        glowPaint.setMaskFilter(new BlurMaskFilter(36f, BlurMaskFilter.Blur.NORMAL));
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(6f);

        anim = ValueAnimator.ofFloat(0f, 360f);
        anim.setDuration(10000); // ~10 sec me poora rainbow
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(v -> {
            hsv[0] = (float) v.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = 6f; // bilkul edge-to-edge (full screen)
        rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
        int col = Color.HSVToColor(hsv);
        glowPaint.setColor(col);
        canvas.drawRoundRect(rect, 56f, 56f, glowPaint);
        corePaint.setColor(col);
        canvas.drawRoundRect(rect, 56f, 56f, corePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }
}
