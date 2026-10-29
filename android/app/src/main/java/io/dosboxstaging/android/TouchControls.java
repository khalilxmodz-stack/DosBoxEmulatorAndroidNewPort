package io.dosboxstaging.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

/**
 * Translucent on-screen DOS keys. Multi-touch: every finger is tracked by pointer id,
 * so e.g. Right + Ctrl (strafe-run + fire) work together. Touches that miss every
 * button are NOT consumed, so they still reach SDL (mouse emulation).
 */
final class TouchControls extends View {
    private static final class Btn {
        final String label; final int keycode; final RectF r = new RectF(); boolean down;
        Btn(String label, int keycode) { this.label = label; this.keycode = keycode; }
    }

    private static final int IMPORT = -1;   // pseudo key: opens the folder importer

    private final Btn[] btns = {
        new Btn("\u25B2", KeyEvent.KEYCODE_DPAD_UP),
        new Btn("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN),
        new Btn("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT),
        new Btn("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT),
        new Btn("CTRL",  KeyEvent.KEYCODE_CTRL_LEFT),
        new Btn("SPACE", KeyEvent.KEYCODE_SPACE),
        new Btn("SHIFT", KeyEvent.KEYCODE_SHIFT_LEFT),
        new Btn("ALT",   KeyEvent.KEYCODE_ALT_LEFT),
        new Btn("ESC",   KeyEvent.KEYCODE_ESCAPE),
        new Btn("ENTER", KeyEvent.KEYCODE_ENTER),
        new Btn("TAB",   KeyEvent.KEYCODE_TAB),
        new Btn("Y",     KeyEvent.KEYCODE_Y),
        new Btn("N",     KeyEvent.KEYCODE_N),
        new Btn("\uD83D\uDCC1", IMPORT),
    };

    private final SparseHolder held = new SparseHolder();
    private final Runnable onImport;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    TouchControls(Context ctx, Runnable onImport) {
        super(ctx);
        this.onImport = onImport;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);
        stroke.setColor(0x80FFFFFF);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    /** pointerId -> button index, without boxing churn (max 10 pointers). */
    private static final class SparseHolder {
        final int[] btnOf = new int[16];
        SparseHolder() { java.util.Arrays.fill(btnOf, -1); }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float u = Math.min(w, h) / 7f;                 // button unit
        float m = u * 0.25f;                           // margin
        text.setTextSize(u * 0.28f);
        // D-pad, bottom-left
        float cx = m + u * 1.5f, cy = h - m - u * 1.5f;
        set(0, cx - u / 2, cy - u * 1.5f, cx + u / 2, cy - u / 2);
        set(1, cx - u / 2, cy + u / 2, cx + u / 2, cy + u * 1.5f);
        set(2, cx - u * 1.5f, cy - u / 2, cx - u / 2, cy + u / 2);
        set(3, cx + u / 2, cy - u / 2, cx + u * 1.5f, cy + u / 2);
        // Action keys, bottom-right: 2x2 grid of large buttons
        float rx = w - m - u * 2.2f, ry = h - m - u * 2.2f;
        set(4, rx, ry, rx + u * 1.05f, ry + u * 1.05f);                       // CTRL
        set(5, rx + u * 1.15f, ry, rx + u * 2.2f, ry + u * 1.05f);            // SPACE
        set(6, rx, ry + u * 1.15f, rx + u * 1.05f, ry + u * 2.2f);            // SHIFT
        set(7, rx + u * 1.15f, ry + u * 1.15f, rx + u * 2.2f, ry + u * 2.2f); // ALT
        // Top row (small): ESC ENTER TAB Y N + import, centred
        float sw = u * 0.9f, sh = u * 0.6f, x0 = (w - 6 * sw - 5 * m) / 2f;
        for (int i = 0; i < 6; i++) set(8 + i, x0 + i * (sw + m), m, x0 + i * (sw + m) + sw, m + sh);
    }

    private void set(int i, float l, float t, float r, float b) { btns[i].r.set(l, t, r, b); }

    @Override
    protected void onDraw(Canvas c) {
        float rad = Math.min(getWidth(), getHeight()) / 28f;
        for (Btn b : btns) {
            fill.setColor(b.down ? 0xB0E0242B : 0x80202024);   // pressed = red accent
            c.drawRoundRect(b.r, rad, rad, fill);
            c.drawRoundRect(b.r, rad, rad, stroke);
            c.drawText(b.label, b.r.centerX(), b.r.centerY() + text.getTextSize() / 3f, text);
        }
    }

    private int hit(float x, float y) {
        for (int i = 0; i < btns.length; i++) if (btns[i].r.contains(x, y)) return i;
        return -1;
    }

    private void press(int i) {
        Btn b = btns[i];
        if (b.down) return;
        b.down = true;
        if (b.keycode == IMPORT) { onImport.run(); b.down = false; }
        else SDLActivity.onNativeKeyDown(b.keycode);
        invalidate();
    }

    private void release(int i) {
        Btn b = btns[i];
        if (!b.down) return;
        b.down = false;
        if (b.keycode != IMPORT) SDLActivity.onNativeKeyUp(b.keycode);
        invalidate();
    }

    private boolean otherPointerHolds(int i, int exceptPointerId) {
        for (int p = 0; p < held.btnOf.length; p++) if (p != exceptPointerId && held.btnOf[p] == i) return true;
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pid = e.getPointerId(idx);
                int i = hit(e.getX(idx), e.getY(idx));
                if (i < 0 || pid >= held.btnOf.length) return false;   // let SDL see it
                held.btnOf[pid] = i;
                press(i);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {                 // slide between D-pad keys
                for (int k = 0; k < e.getPointerCount(); k++) {
                    int pid = e.getPointerId(k);
                    if (pid >= held.btnOf.length || held.btnOf[pid] < 0) continue;
                    int now = hit(e.getX(k), e.getY(k)), was = held.btnOf[pid];
                    if (now != was) {
                        held.btnOf[pid] = now;
                        if (!otherPointerHolds(was, pid)) release(was);
                        if (now >= 0) press(now);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int pid = e.getPointerId(idx);
                if (pid < held.btnOf.length && held.btnOf[pid] >= 0) {
                    int was = held.btnOf[pid];
                    held.btnOf[pid] = -1;
                    if (!otherPointerHolds(was, pid)) release(was);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                return true;
            default:
                return false;
        }
    }

    /** Release every held key (pause, focus loss, touch cancel, view detach). */
    void releaseAll() {
        java.util.Arrays.fill(held.btnOf, -1);
        for (int i = 0; i < btns.length; i++) release(i);
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}
