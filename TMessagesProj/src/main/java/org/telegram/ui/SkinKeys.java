package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.skin.motion.SkinHaptics;
import org.telegram.skin.motion.SkinMotion;
import org.telegram.skin.theme.CanvasMaterials;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * A Telegram menu restyled as a slab of the skin's keys: the aluminium slab
 * it sits on, and the celadon porcelain set into the key under the finger,
 * its LED lit. The menu keeps its own rows, touch and keyboard handling;
 * {@link Inlay} only adds a drag that began outside it.
 */
public final class SkinKeys {

    /** How far the slab sits inside a Telegram popup's 8dp margin, leaving room for its shadow. */
    private static final float SLAB_INSET = 4;
    private static final float SLAB_RADIUS = 22;
    /** Concentric with the slab: its radius less the gap between the slab's edge and the rows. */
    private static final float INLAY_RADIUS = SLAB_RADIUS - (8 - SLAB_INSET);

    private SkinKeys() {
    }

    public static Drawable slab(CanvasMaterials m) {
        return new Slab(m);
    }

    /**
     * The porcelain inlay of a slab's keys, as one piece. It seats itself under
     * the key that is pressed or selected, slides from key to key as the finger
     * or the keyboard moves on, and fades where it is when none is chosen. Each
     * row draws the part of it lying over that row, so it also passes behind a
     * groove between groups. A drag that began outside the slab (the long press
     * that opened it) is followed with {@link #drag} and ended with
     * {@link #release} or {@link #cancel}.
     */
    public static final class Inlay {
        private final CanvasMaterials m;
        private final ArrayList<View> rows = new ArrayList<>();
        private final ArrayList<View> keys = new ArrayList<>();
        private final RectF rect = new RectF();
        private final int[] location = new int[2];
        private View target;
        // Top and bottom in the rows' parent, seating by spring from wherever the inlay was.
        private float fromTop, fromBottom, toTop, toBottom;
        private long moveStart;
        private boolean moving;
        // Glazes in when a key is chosen, fades out when none is; either from its current strength.
        private boolean shown;
        private float fromPresence;
        private long presenceStart;
        private long invalidatedAt;

        public Inlay(CanvasMaterials m) {
            this.m = m;
        }

        /** The background for [row]: a [key] can take the inlay, any other row (a groove) only shows it passing. */
        public Drawable background(View row, boolean key) {
            rows.add(row);
            if (key) {
                keys.add(row);
            }
            return new Row(this, row, key);
        }

        /** Follows a finger at raw screen [x], [y]; the key under it takes the inlay with a tick. */
        public void drag(float x, float y) {
            final View key = keyAt(x, y);
            if (key != null && key != target) {
                SkinHaptics.tap(key);
            }
            setTarget(key);
        }

        /** Ends the drag at raw screen [x], [y]: the key under the finger acts. Returns whether one did. */
        public boolean release(float x, float y) {
            final View key = keyAt(x, y);
            setTarget(null);
            if (key == null) {
                return false;
            }
            key.performClick();
            return true;
        }

        public void cancel() {
            setTarget(null);
        }

        private View keyAt(float x, float y) {
            for (int i = 0; i < keys.size(); i++) {
                final View key = keys.get(i);
                if (key.getVisibility() != View.VISIBLE || !key.isEnabled() || !key.isAttachedToWindow()) {
                    continue;
                }
                key.getLocationOnScreen(location);
                if (x >= location[0] && x < location[0] + key.getWidth() && y >= location[1] && y < location[1] + key.getHeight()) {
                    return key;
                }
            }
            return null;
        }

        private void choose(View row, boolean chosen) {
            if (chosen) {
                setTarget(row);
            } else if (target == row) {
                setTarget(null);
            }
        }

        private void setTarget(View row) {
            if (row == target) {
                return;
            }
            final long now = SystemClock.uptimeMillis();
            final boolean reduced = SkinMotion.reduced();
            if (row != null) {
                if (presence(now) <= 0f || reduced) {
                    // Nothing showing: it appears in place rather than sliding in from an old key.
                    fromTop = toTop = row.getTop();
                    fromBottom = toBottom = row.getBottom();
                    moving = false;
                } else {
                    fromTop = position(fromTop, toTop, now);
                    fromBottom = position(fromBottom, toBottom, now);
                    toTop = row.getTop();
                    toBottom = row.getBottom();
                    moveStart = now;
                    moving = true;
                }
            }
            final boolean show = row != null;
            if (show != shown) {
                fromPresence = presence(now);
                shown = show;
                presenceStart = reduced ? now - 1000 : now;
            }
            target = row;
            invalidateRows(now);
        }

        private float position(float from, float to, long now) {
            if (!moving) {
                return to;
            }
            return from + (to - from) * SkinMotion.springAt(SkinMotion.SEAT, now - moveStart);
        }

        private float presence(long now) {
            final float t = now - presenceStart;
            if (shown) {
                final float f = Math.min(1f, t / SkinMotion.GLAZE_MS);
                return fromPresence + (1f - fromPresence) * SkinMotion.GLAZE.getInterpolator().getInterpolation(f);
            }
            final float f = Math.min(1f, t / SkinMotion.EXIT_MS);
            return fromPresence * (1f - SkinMotion.EXIT.getInterpolator().getInterpolation(f));
        }

        private boolean isMoving(long now) {
            if (moving && now - moveStart >= SkinMotion.SEAT.getSettleMs()) {
                moving = false;
            }
            return moving || now - presenceStart < (shown ? SkinMotion.GLAZE_MS : SkinMotion.EXIT_MS);
        }

        private void invalidateRows(long now) {
            if (invalidatedAt == now) {
                return;
            }
            invalidatedAt = now;
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).invalidate();
            }
        }

        private void draw(View row, Canvas canvas, Rect bounds, int alpha) {
            final long now = SystemClock.uptimeMillis();
            final float a = presence(now) * alpha / 255f;
            if (a > 0f) {
                final float top = position(fromTop, toTop, now) - row.getTop();
                final float bottom = position(fromBottom, toBottom, now) - row.getTop();
                // Drawn from a fixed origin, so the glaze's gradient is not rebuilt as it slides.
                canvas.save();
                canvas.translate(bounds.left, top);
                rect.set(0, 0, bounds.width(), bottom - top);
                SkinMetal.drawPorcelain(canvas, rect, Math.min(dpf2(INLAY_RADIUS), rect.height() / 2f), m, true, a);
                final float led = dpf2(3);
                SkinStyle.drawLed(canvas, rect.right - dpf2(12) - led, rect.centerY(), led, true, m.led, m.textTertiary, a);
                canvas.restore();
            }
            if (isMoving(now)) {
                invalidateRows(now);
            }
        }
    }

    /** A search field's ground: a well sunk into the metal, corner radius [radiusDp]. */
    public static Drawable well(CanvasMaterials m, float radiusDp) {
        return new Well(m, radiusDp);
    }

    /**
     * A page's button: the main action as porcelain, any other as an aluminium
     * key, with a press ripple in the key's own ink.
     */
    public static Drawable button(CanvasMaterials m, boolean main, float radiusDp) {
        final Drawable key = new Key(m, main, radiusDp);
        final int ink = main ? m.porcelainText : m.text;
        return new RippleDrawable(ColorStateList.valueOf(Theme.multAlpha(ink, 0.12f)), key, null);
    }

    private abstract static class Base extends Drawable {
        final CanvasMaterials m;
        int alpha = 255;

        Base(CanvasMaterials m) {
            this.m = m;
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class Slab extends Base {
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private float sheenTop = Float.NaN;
        private float sheenBottom = Float.NaN;

        Slab(CanvasMaterials m) {
            super(m);
            shadowPaint.setColor(m.sheenMid);
            edgePaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            // The popup lays its rows out with its own 8dp padding.
            padding.set(0, 0, 0, 0);
            return false;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            final float inset = dpf2(SLAB_INSET);
            final float radius = dpf2(SLAB_RADIUS);
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            final float a = alpha / 255f;

            if (alpha == 255) {
                shadowPaint.setShadowLayer(dpf2(4), 0, dpf2(1.5f), m.castShadow);
                canvas.drawRoundRect(rect, radius, radius, shadowPaint);
            }
            if (sheenTop != rect.top || sheenBottom != rect.bottom) {
                sheenTop = rect.top;
                sheenBottom = rect.bottom;
                sheenPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        new int[]{m.sheenTop, m.sheenMid, m.sheenBottom}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            }
            sheenPaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);

            clip.rewind();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            final float px = dpf2(1);
            edgePaint.setStrokeWidth(px);
            edgePaint.setColor(m.highlight);
            edgePaint.setAlpha((int) (Color.alpha(m.highlight) * a));
            canvas.drawLine(rect.left, rect.top + px / 2, rect.right, rect.top + px / 2, edgePaint);
            edgePaint.setColor(m.grooveCut);
            edgePaint.setAlpha((int) (Color.alpha(m.grooveCut) * a));
            canvas.drawLine(rect.left, rect.bottom - px / 2, rect.right, rect.bottom - px / 2, edgePaint);
            canvas.restore();
        }
    }

    private static final class Well extends Base {
        private final RectF rect = new RectF();
        private final float radiusDp;

        Well(CanvasMaterials m, float radiusDp) {
            super(m);
            this.radiusDp = radiusDp;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            SkinMetal.drawWell(canvas, rect, Math.min(dpf2(radiusDp), rect.height() / 2f), m, alpha / 255f);
        }
    }

    private static final class Key extends Base {
        private final RectF rect = new RectF();
        private final boolean porcelain;
        private final float radiusDp;

        Key(CanvasMaterials m, boolean porcelain, float radiusDp) {
            super(m);
            this.porcelain = porcelain;
            this.radiusDp = radiusDp;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            final float radius = Math.min(dpf2(radiusDp), rect.height() / 2f);
            if (porcelain) {
                SkinMetal.drawPorcelain(canvas, rect, radius, m, true, alpha / 255f);
            } else {
                rect.inset(0, dpf2(1));
                SkinMetal.drawKey(canvas, rect, radius, m, alpha / 255f);
            }
        }
    }

    /** A row's share of the {@link Inlay}; a key row also tells it when it is pressed or selected. */
    private static final class Row extends Base {
        private final Inlay inlay;
        private final View row;
        private final boolean key;

        Row(Inlay inlay, View row, boolean key) {
            super(inlay.m);
            this.inlay = inlay;
            this.row = row;
            this.key = key;
        }

        @Override
        public boolean isStateful() {
            return key;
        }

        @Override
        protected boolean onStateChange(@NonNull int[] state) {
            boolean chosen = false;
            for (int s : state) {
                if (s == android.R.attr.state_pressed || s == android.R.attr.state_selected) {
                    chosen = true;
                    break;
                }
            }
            inlay.choose(row, chosen);
            return false;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (!getBounds().isEmpty()) {
                inlay.draw(row, canvas, getBounds(), alpha);
            }
        }
    }
}
