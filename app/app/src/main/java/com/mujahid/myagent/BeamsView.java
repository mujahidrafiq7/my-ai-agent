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
 * v86: Pill buttons se reactor tak — ab solid line NAHI, rang-birange
 * DOTS ki dotted line hai jo loop me behti rehti hai.
 * Seamless tareeqa: dots "spacing" ke faasle pe hain aur offset bhi
 * spacing ke andar hi ghoomta hai — wrap pe pattern bilkul waisa ka
 * waisa, is liye jhatka kabhi nazar nahi ata.
 * Idle = slow (2400ms/cycle), conversation active = fast (650ms/cycle)
 * — ReactorView ke setActive pattern pe.
 */
public class BeamsView extends View {

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] sx, sy;
    private int[] colors;
    private float ex, ey;
    private boolean ready = false;

    private ValueAnimator animator;
    private float progress = 0f;

    /** Har dot ka rang — beam ke apne rang + mazeed rang, baari baari. */
    private static final int[] PALETTE = {
            0xFF3C8CFF, 0xFFFF9632, 0xFFAAB4BE, 0xFF50DC82,
            0xFF00E5FF, 0xFFFF4FD8, 0xFFFFD84D
    };

    public BeamsView(Context c, AttributeSet a) {
        super(c, a);
        dotPaint.setStyle(Paint.Style.FILL);
        haloPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
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

    /** Conversation active ho to dots tez, warna slow. */
    public void setActive(boolean on) {
        if (animator != null) animator.setDuration(on ? 650 : 2400);
    }

    /** Har beam: start (button ka right edge), color; end = reactor ka center. */
    public void setBeams(float[] startX, float[] startY, int[] beamColors,
                         float endX, float endY) {
        this.sx = startX;
        this.sy = startY;
        this.colors = beamColors;
        this.ex = endX;
        this.ey = endY;
        this.ready = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!ready || sx == null) return;
        float density = getResources().getDisplayMetrics().density;
        float spacing = 30f * density;
        float r = 3.4f * density;
        float offset = progress * spacing;
        for (int i = 0; i < sx.length; i++) {
            float dx = ex - sx[i];
            float dy = ey - sy[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) continue;
            int steps = (int) (len / spacing) + 1;
            for (int k = 0; k <= steps; k++) {
                float d = k * spacing + offset - spacing;
                if (d < 0f || d > len) continue;
                float x = sx[i] + dx * (d / len);
                float y = sy[i] + dy * (d / len);
                int col = PALETTE[(k + i * 2) % PALETTE.length];
                haloPaint.setColor(Color.argb(50,
                        Color.red(col), Color.green(col), Color.blue(col)));
                canvas.drawCircle(x, y, r * 2.3f, haloPaint);
                dotPaint.setColor(col);
                canvas.drawCircle(x, y, r, dotPaint);
            }
        }
    }
}
