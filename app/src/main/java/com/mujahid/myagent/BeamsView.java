package com.mujahid.myagent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Pill buttons se reactor tak energy beams — video jaisi.
 * setBeams() me har beam ka start point, color milta hai; end = reactor center.
 * (Animation: user ki pasand ki video aane pe waisa banayenge.)
 */
public class BeamsView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] sx, sy;
    private int[] colors;
    private float ex, ey;
    private boolean ready = false;

    public BeamsView(Context c, AttributeSet a) {
        super(c, a);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(10f);
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(3f);
        dotPaint.setStyle(Paint.Style.FILL);
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
        for (int i = 0; i < sx.length; i++) {
            int col = colors[i];
            glowPaint.setColor(Color.argb(70, Color.red(col), Color.green(col), Color.blue(col)));
            canvas.drawLine(sx[i], sy[i], ex, ey, glowPaint);
            corePaint.setColor(Color.argb(230, Color.red(col), Color.green(col), Color.blue(col)));
            canvas.drawLine(sx[i], sy[i], ex, ey, corePaint);
            dotPaint.setColor(Color.argb(255, Color.red(col), Color.green(col), Color.blue(col)));
            canvas.drawCircle(ex, ey, 6f, dotPaint);
        }
    }
}
