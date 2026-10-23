package io.sundartv;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * The settings screen's background: a mountain lake at dusk behind frosted glass. Painted at half size (sky, sun,
 * clouds, three ranges of mountains in the haze, the lake reflecting them, pines on the shore), lightly frosted,
 * scaled up with a fine grain; over the menu, a darker frosted glass panel so its text stays crisp. Built once, off
 * the UI thread, then kept only in GPU memory.
 */
final class Scenery {
    private Scenery() {}

    /** The menu's glass panel, in 1920×1080 units (the settings list runs from x 1000 to 1824). */
    static final float PANEL_L = 952, PANEL_T = 92, PANEL_R = 1872, PANEL_B = 1016, PANEL_RADIUS = 40;

    static Bitmap make(int width, int height) {
        int w = Math.max(2, width), h = Math.max(2, height);
        int sw = 960, sh = 540;
        float horizon = sh * 0.64f;
        Random rnd = new Random(42);
        Bitmap img = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(img);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Sky: indigo at the top, through violet and rose, to peach and gold at the horizon.
        p.setShader(new LinearGradient(0, 0, 0, horizon,
                new int[] {0xFF141C46, 0xFF2E2F6E, 0xFF6B4C93, 0xFFC0698A, 0xFFF29E6D, 0xFFFFD39A},
                new float[] {0, 0.22f, 0.48f, 0.7f, 0.88f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, sw, horizon, p);
        // The first stars.
        p.setShader(null);
        for (int i = 0; i < 70; i++) {
            float x = rnd.nextFloat() * sw, y = (float) Math.pow(rnd.nextFloat(), 1.8) * horizon * 0.45f;
            p.setColor(Ui.alpha(0xFFFFFFFF, 0.25f + rnd.nextFloat() * 0.5f));
            c.drawCircle(x, y, 0.5f + rnd.nextFloat() * 0.9f, p);
        }
        // The sun, low, behind the mountains (it glows through the menu's glass).
        float sunX = sw * 0.66f, sunY = horizon - sh * 0.06f;
        glow(c, p, sunX, sunY, sw * 0.42f, 0xFFFFB27A, 0.55f);
        glow(c, p, sunX, sunY, sw * 0.12f, 0xFFFFE6B8, 0.9f);
        p.setShader(null);
        p.setColor(0xFFFFF4DC);
        c.drawCircle(sunX, sunY, sw * 0.028f, p);
        // Long thin clouds, lit pink from below.
        for (int i = 0; i < 7; i++) {
            float cy = horizon * (0.3f + rnd.nextFloat() * 0.45f), cx = rnd.nextFloat() * sw;
            float cw = sw * (0.12f + rnd.nextFloat() * 0.2f), ch = sh * (0.008f + rnd.nextFloat() * 0.012f);
            p.setShader(new LinearGradient(0, cy - ch, 0, cy + ch, Ui.alpha(0xFFFFC2C9, 0.15f), Ui.alpha(0xFFFFB08A, 0.55f),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(cx - cw / 2, cy - ch, cx + cw / 2, cy + ch), ch, ch, p);
        }

        // Three ranges, far to near: hazier and lighter far away.
        ridge(c, p, rnd, sw, horizon, sh * 0.30f, 0.011f, 0xFFA481B4, 0xFFD39CA7);
        ridge(c, p, rnd, sw, horizon, sh * 0.22f, 0.016f, 0xFF6A5490, 0xFF9A6E98);
        ridge(c, p, rnd, sw, horizon, sh * 0.13f, 0.024f, 0xFF2E2B58, 0xFF4A3B66);
        // Mist on the water line.
        p.setShader(new LinearGradient(0, horizon - sh * 0.05f, 0, horizon + sh * 0.02f,
                new int[] {0, Ui.alpha(0xFFFFD6C0, 0.35f), 0}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon - sh * 0.05f, sw, horizon + sh * 0.02f, p);

        // The lake: everything above, mirrored, a little darker and bluer, with ripples and the sun's path.
        Bitmap above = Bitmap.createBitmap(img, 0, 0, sw, Math.round(horizon));
        c.save();
        c.clipRect(0, horizon, sw, sh);
        c.translate(0, 2 * horizon);
        c.scale(1, -1);
        p.setShader(null);
        p.setAlpha(235);
        c.drawBitmap(above, 0, 0, p);
        p.setAlpha(255);
        c.restore();
        above.recycle();
        p.setShader(new LinearGradient(0, horizon, 0, sh, Ui.alpha(0xFF0E1638, 0.25f), Ui.alpha(0xFF0A0F26, 0.7f),
                Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, sw, sh, p);
        p.setShader(new LinearGradient(sunX - sw * 0.05f, 0, sunX + sw * 0.05f, 0,
                new int[] {0, Ui.alpha(0xFFFFD9A8, 0.35f), 0}, null, Shader.TileMode.CLAMP));
        c.drawRect(sunX - sw * 0.05f, horizon, sunX + sw * 0.05f, sh, p);
        p.setShader(null);
        for (int i = 0; i < 60; i++) {
            float y = horizon + (float) Math.pow(rnd.nextFloat(), 0.7) * (sh - horizon);
            float x = rnd.nextFloat() * sw, len = sw * (0.02f + rnd.nextFloat() * 0.08f) * (y - horizon) / (sh - horizon);
            p.setColor(Ui.alpha(0xFFFFE2C4, 0.06f + rnd.nextFloat() * 0.1f));
            c.drawRect(x, y, x + len, y + 0.8f, p);
        }

        // The near shore with pines, on both sides.
        shore(c, p, rnd, sw, sh, horizon, true);
        shore(c, p, rnd, sw, sh, horizon, false);

        // Frosted, lightly: the scene stays recognisable.
        int[] px = new int[sw * sh];
        img.getPixels(px, 0, sw, 0, 0, sw, sh);
        blur(px, sw, sh, 2, 2);
        img.setPixels(px, 0, sw, 0, 0, sw, sh);
        // The panel's glass: much more frosted.
        Bitmap frost = Bitmap.createScaledBitmap(img, sw / 4, sh / 4, true);
        int fw = frost.getWidth(), fh = frost.getHeight();
        int[] fpx = new int[fw * fh];
        frost.getPixels(fpx, 0, fw, 0, 0, fw, fh);
        blur(fpx, fw, fh, 5, 3);
        frost.setPixels(fpx, 0, fw, 0, 0, fw, fh);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas o = new Canvas(out);
        Rect full = new Rect(0, 0, w, h);
        o.drawBitmap(img, null, full, p);
        img.recycle();
        // The whole glass: a faint frost veil, and a little darker at the edges, where the text sits.
        p.setShader(null);
        p.setColor(Ui.alpha(0xFFE8ECFF, 0.06f));
        o.drawRect(full, p);
        p.setShader(new RadialGradient(w * 0.3f, h * 0.45f, w * 0.75f, new int[] {0, Ui.alpha(0xFF050816, 0.35f)},
                null, Shader.TileMode.CLAMP));
        o.drawRect(full, p);
        grain(out, 4f, rnd);

        // The menu's frosted panel: a soft shadow, the strong frost, a dark tint, a light edge.
        float k = w / 1920f;
        RectF panel = new RectF(PANEL_L * k, PANEL_T * k, PANEL_R * k, PANEL_B * k);
        float r = PANEL_RADIUS * k;
        p.setShader(null);
        p.setColor(Ui.alpha(0xFF000000, 0.35f));
        p.setMaskFilter(new BlurMaskFilter(40 * k, BlurMaskFilter.Blur.NORMAL));
        o.drawRoundRect(new RectF(panel.left, panel.top + 14 * k, panel.right, panel.bottom + 14 * k), r, r, p);
        p.setMaskFilter(null);
        o.save();
        Path clip = new Path();
        clip.addRoundRect(panel, r, r, Path.Direction.CW);
        o.clipPath(clip);
        o.drawBitmap(frost, null, full, p);
        p.setColor(Ui.alpha(0xFF0A0E24, 0.42f));
        o.drawRect(panel, p);
        p.setShader(new LinearGradient(0, panel.top, 0, panel.top + 160 * k, Ui.alpha(0xFFFFFFFF, 0.08f), 0,
                Shader.TileMode.CLAMP));
        o.drawRect(panel, p);
        o.restore();
        frost.recycle();
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * k);
        p.setColor(Ui.alpha(0xFFFFFFFF, 0.16f));
        o.drawRoundRect(panel, r, r, p);
        p.setStyle(Paint.Style.FILL);
        return Backdrop.gpu(out);
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int color, float a) {
        p.setShader(new RadialGradient(x, y, r, new int[] {Ui.alpha(color, a), Ui.alpha(color, a * 0.3f), 0},
                new float[] {0, 0.4f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    /** A mountain range: sharp peaks (1 - |sin| sums), filled with a gradient from its peaks to the haze below. */
    private static void ridge(Canvas c, Paint p, Random rnd, int sw, float base, float amp, float f, int top, int bottom) {
        float p1 = rnd.nextFloat() * 6, p2 = rnd.nextFloat() * 6, p3 = rnd.nextFloat() * 6;
        Path path = new Path();
        path.moveTo(0, base);
        float peak = base;
        for (int x = 0; x <= sw; x += 2) {
            double v = 0.58 * (1 - Math.abs(Math.sin(x * f + p1))) + 0.3 * (1 - Math.abs(Math.sin(x * f * 2.3 + p2)))
                    + 0.12 * Math.sin(x * f * 5.1 + p3);
            float y = base - amp * (float) v;
            peak = Math.min(peak, y);
            path.lineTo(x, y);
        }
        path.lineTo(sw, base + 1);
        path.lineTo(0, base + 1);
        path.close();
        p.setShader(new LinearGradient(0, peak, 0, base, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
    }

    /** A dark shore rising at one side, with pines on it. */
    private static void shore(Canvas c, Paint p, Random rnd, int sw, int sh, float horizon, boolean left) {
        float edge = sw * (left ? 0.3f : 0.24f);
        Path land = new Path();
        float x0 = left ? 0 : sw, dir = left ? 1 : -1;
        land.moveTo(x0, sh);
        land.lineTo(x0, horizon + sh * 0.08f);
        land.cubicTo(x0 + dir * edge * 0.4f, horizon + sh * 0.1f, x0 + dir * edge * 0.8f, sh * 0.86f, x0 + dir * edge, sh);
        land.close();
        p.setShader(null);
        p.setColor(0xFF0B0D1E);
        c.drawPath(land, p);
        int trees = left ? 9 : 6;
        for (int i = 0; i < trees; i++) {
            float t = i / (float) trees;
            float x = x0 + dir * edge * (0.05f + t * 0.75f);
            float ground = horizon + sh * 0.09f + (sh * 0.86f - horizon - sh * 0.09f) * t * t;
            float height = sh * (0.16f + rnd.nextFloat() * 0.12f) * (1 - t * 0.45f);
            pine(c, p, x, ground, height);
        }
    }

    private static void pine(Canvas c, Paint p, float x, float ground, float height) {
        p.setColor(0xFF080A18);
        c.drawRect(x - height * 0.015f, ground - height * 0.1f, x + height * 0.015f, ground, p);
        Path tree = new Path();
        int tiers = 5;
        for (int i = 0; i < tiers; i++) {
            float bottom = ground - height * (0.08f + 0.17f * i), top = bottom - height * 0.3f;
            float half = height * 0.17f * (1 - i / (float) (tiers + 1));
            tree.moveTo(x - half, bottom);
            tree.lineTo(x, top);
            tree.lineTo(x + half, bottom);
            tree.close();
        }
        c.drawPath(tree, p);
    }

    /** Grain and a Bayer dither (no banding once scaled up). */
    private static void grain(Bitmap out, float amount, Random rnd) {
        int w = out.getWidth(), h = out.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            out.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                float d = (Ui.BAYER[(y & 3) * 4 + (x & 3)] - 7.5f) / 16f + (rnd.nextFloat() - 0.5f) * amount;
                int q = row[x], v = 0xFF000000;
                for (int s = 0; s < 24; s += 8) v |= Math.max(0, Math.min(255, Math.round(((q >>> s) & 255) + d))) << s;
                row[x] = v;
            }
            out.setPixels(row, 0, w, 0, y, w, 1);
        }
    }

    private static void blur(int[] px, int w, int h, int r, int passes) {
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < passes; pass++) {
            box(px, tmp, w, h, r, true);
            box(tmp, px, w, h, r, false);
        }
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w, step = horizontal ? 1 : w, lineStep = horizontal ? w : 1;
        float k = 1f / (2 * r + 1);
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep;
            float sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int q = in[base + Math.max(0, Math.min(n - 1, i)) * step];
                sr += (q >> 16) & 255;
                sg += (q >> 8) & 255;
                sb += q & 255;
            }
            for (int i = 0; i < n; i++) {
                out[base + i * step] = 0xFF000000 | (Math.round(sr * k) << 16) | (Math.round(sg * k) << 8) | Math.round(sb * k);
                int add = in[base + Math.min(n - 1, i + r + 1) * step], sub = in[base + Math.max(0, i - r) * step];
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }
}
