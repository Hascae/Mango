package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import org.telegram.skin.theme.CanvasMaterials;
import org.telegram.skin.theme.CanvasPalette;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.utils.glass.source.GlassSourceColor;

/**
 * A machined-aluminium slab in place of Telegram's glass: the input console,
 * the floating buttons and panels of the chat screen. It is still the glass
 * drawable as far as its shape goes (bounds, padding, corner radii, arrow), so
 * every caller lays out exactly as before, and draws the skin's metal along
 * that outline: the floating shadow, a vertical sheen, a lit top edge and a
 * cut along the bottom.
 */
public class SkinMetalBackground extends BlurredBackgroundDrawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private BlurredBackgroundColorProvider colorProvider;
    private float shadowAlpha = 1f;

    private CanvasMaterials sheenMaterials;
    private int sheenTop;
    private int sheenBottom;

    public SkinMetalBackground() {
        // Metal shows nothing of what lies behind it: a plain colour source keeps the glass
        // engine's render paths idle.
        super(new GlassSourceColor());
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Math.max(1f, dpf2(1)));
    }

    @Override
    public BlurredBackgroundDrawable setColorProvider(BlurredBackgroundColorProvider colorProvider) {
        this.colorProvider = colorProvider;
        return super.setColorProvider(colorProvider);
    }

    @Override
    public void setShadowAlpha(float alpha) {
        shadowAlpha = alpha;
        super.setShadowAlpha(alpha);
    }

    @Override
    public boolean shouldHaveDisplayList() {
        return false;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getPaddedBounds();
        final int alpha = getAlpha();
        if (bounds.isEmpty() || alpha == 0) {
            return;
        }
        final Path path = getPath();
        // Without a provider there is no panel colour to read, so follow the app theme.
        final CanvasMaterials m = colorProvider != null
                ? SkinMetal.materials(colorProvider.getBackgroundColor())
                : CanvasPalette.of(Theme.isCurrentThemeDark());

        if (alpha == 255 && shadowAlpha > 0) {
            shadowPaint.setColor(m.sheenMid);
            for (int i = m.floatingBlur.length - 1; i >= 0; i--) {
                shadowPaint.setShadowLayer(dpf2(m.floatingBlur[i]), 0, dpf2(m.floatingDrop[i]),
                        Theme.multAlpha(m.floatingColor[i], shadowAlpha));
                canvas.drawPath(path, shadowPaint);
            }
        }

        final boolean faded = alpha != 255;
        if (faded) {
            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
        }
        if (sheenMaterials != m || sheenTop != bounds.top || sheenBottom != bounds.bottom) {
            sheenMaterials = m;
            sheenTop = bounds.top;
            sheenBottom = bounds.bottom;
            fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    new int[]{m.sheenTop, m.sheenMid, m.sheenBottom}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawPath(path, fillPaint);

        // The lit edge runs along the upper half of the outline and the cut along the lower
        // half, each kept inside the slab.
        final float middle = bounds.exactCenterY();
        drawEdge(canvas, path, bounds.top - 1, middle, m.highlight);
        drawEdge(canvas, path, middle, bounds.bottom + 1, m.grooveCut);
        if (faded) {
            canvas.restore();
        }
    }

    private void drawEdge(Canvas canvas, Path path, float top, float bottom, int color) {
        final Rect bounds = getPaddedBounds();
        canvas.save();
        canvas.clipRect(bounds.left - 1, top, bounds.right + 1, bottom);
        canvas.clipPath(path);
        edgePaint.setColor(color);
        canvas.drawPath(path, edgePaint);
        canvas.restore();
    }
}
