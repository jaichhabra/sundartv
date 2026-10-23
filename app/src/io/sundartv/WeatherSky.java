package io.sundartv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * The wallpaper when there's no photo: the sky outside, from the live weather (Open-Meteo's WMO code), the way Apple
 * Weather does it. Each condition has a day and a night sky: clear (a sun glow; stars at night), clouds, fog, rain
 * and drizzle (a rainy window: frosted glass with drops, city lights behind it at night), snow and storms.
 * The sky is drawn small (soft for free), scaled up and dithered, once per condition, off the UI thread; then it
 * lives only in GPU memory. Rain, snow and lightning move on top of it ({@link Precip}).
 * By day the skies stay light enough for the dark text; at night the text is white.
 */
final class WeatherSky {
    private WeatherSky() {}

    static final int NONE = -1, CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, HEAVY = 6, SNOW = 7,
            STORM = 8;

    static int kind(HomeData.Weather wx) {
        if (wx == null || wx.code < 0) return NONE;
        int c = wx.code;
        if (c <= 1) return CLEAR;
        if (c == 2) return PARTLY;
        if (c == 3) return CLOUDY;
        if (c == 45 || c == 48) return FOG;
        if (c >= 51 && c <= 57) return DRIZZLE;
        if (c == 65 || c == 67 || c == 82) return HEAVY;
        if (c >= 61 && c <= 67 || c >= 80 && c <= 82) return RAIN;
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return SNOW;
        if (c >= 95) return STORM;
        return CLOUDY;
    }

    /** Blue by day: the text on it is white (elsewhere it's dark). */
    static boolean blue(int kind) {
        return kind == CLEAR || kind == PARTLY;
    }

    private static boolean wet(int kind) {
        return kind == DRIZZLE || kind == RAIN || kind == HEAVY || kind == STORM;
    }

    /** The sky's top and bottom colors, by day (first pair) and at night. */
    private static int[] colors(int kind, boolean night) {
        switch (kind) {
            case CLEAR:   return night ? new int[] {0xFF050E26, 0xFF1A3060} : new int[] {0xFF3F8FE0, 0xFF9CCBF3};
            case PARTLY:  return night ? new int[] {0xFF0A152F, 0xFF233660} : new int[] {0xFF4E97DD, 0xFFA8CFEF};
            case CLOUDY:  return night ? new int[] {0xFF1A2130, 0xFF303A4B} : new int[] {0xFF9DB0C3, 0xFFD6E0E9};
            case FOG:     return night ? new int[] {0xFF252B34, 0xFF3D444E} : new int[] {0xFFC2CBD3, 0xFFE5E8EC};
            case DRIZZLE: return night ? new int[] {0xFF101B2B, 0xFF223044} : new int[] {0xFF98AABB, 0xFFCFD9E3};
            case RAIN:    return night ? new int[] {0xFF0D1828, 0xFF1E2B3D} : new int[] {0xFF8C9FB2, 0xFFC8D3DE};
            case HEAVY:   return night ? new int[] {0xFF0A1322, 0xFF1A2536} : new int[] {0xFF8293A6, 0xFFBFCAD5};
            case SNOW:    return night ? new int[] {0xFF1E293B, 0xFF3A4860} : new int[] {0xFFB2C2D2, 0xFFE8EDF3};
            default:      return night ? new int[] {0xFF0C1120, 0xFF1D2434} : new int[] {0xFF8F9BAA, 0xFFC0C8D2};
        }
    }

    /** The sky is drawn this small: blurring it is cheap, and it's all soft shapes anyway. */
    private static final int SW = 320, SH = 180;

    private static final int[] CITY_LIGHTS = {0xFFFFA94D, 0xFFFF5E5B, 0xFF4FD1C5, 0xFFFFE8C2, 0xFF5B8CFF, 0xFFFFC857};

    /** Builds the sky (slow: call it off the UI thread). */
    static Shader make(int width, int height, int kind, boolean night) {
        int w = Math.max(2, width), h = Math.max(2, height);
        float k = h / 1080f;
        Random rnd = new Random(kind * 31 + (night ? 7 : 3));
        Bitmap small = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        int[] sky = colors(kind, night);
        p.setShader(new LinearGradient(0, 0, 0, SH, sky, null, Shader.TileMode.CLAMP));
        sc.drawRect(0, 0, SW, SH, p);

        if (kind == CLEAR || kind == PARTLY) {
            // The sun (or the moon's glow) at the top right.
            float a = night ? 0.18f : 0.75f;
            glow(sc, p, SW * 0.84f, SH * 0.06f, SW * 0.55f, night ? 0xFFB8C8FF : 0xFFFFF4DC, a);
            if (!night) glow(sc, p, SW * 0.84f, SH * 0.06f, SW * 0.12f, 0xFFFFFFFF, 0.9f);
        }
        if (kind == PARTLY) clouds(sc, p, rnd, 4, night ? 0xFF3C4A68 : 0xFFFFFFFF, night ? 0.75f : 0.85f, 0.05f, 0.45f);
        if (kind == CLOUDY) {
            clouds(sc, p, rnd, 5, night ? 0xFF283143 : 0xFFB6C3D0, 0.8f, -0.05f, 0.35f);
            clouds(sc, p, rnd, 6, night ? 0xFF3A4458 : 0xFFF3F6F9, night ? 0.6f : 0.85f, 0.1f, 0.7f);
        }
        if (kind == SNOW) clouds(sc, p, rnd, 6, night ? 0xFF46536B : 0xFFF5F8FB, 0.6f, -0.05f, 0.4f);
        if (kind == STORM) {
            clouds(sc, p, rnd, 7, night ? 0xFF1A2030 : 0xFF6E7A8A, 0.85f, -0.08f, 0.4f);
            clouds(sc, p, rnd, 4, night ? 0xFF2A3346 : 0xFFA9B3BF, 0.6f, 0.15f, 0.6f);
        }
        if (kind == FOG) {
            for (int i = 0; i < 7; i++) {
                float y = SH * (0.2f + i * 0.12f + rnd.nextFloat() * 0.05f);
                p.setShader(new LinearGradient(0, y - SH * 0.08f, 0, y + SH * 0.08f,
                        new int[] {0, Ui.alpha(night ? 0xFF5A626E : 0xFFFFFFFF, 0.55f), 0}, null, Shader.TileMode.CLAMP));
                sc.drawRect(0, y - SH * 0.08f, SW, y + SH * 0.08f, p);
            }
        }
        if (wet(kind)) {
            clouds(sc, p, rnd, 5, night ? 0xFF2A3548 : 0xFFE2E8EE, 0.55f, -0.05f, 0.5f);
            if (night) {
                for (int i = 0; i < 40; i++) {
                    float x = rnd.nextFloat() * SW, y = SH * (0.35f + rnd.nextFloat() * 0.65f), r = 3 + rnd.nextFloat() * 11;
                    glow(sc, p, x, y, r, CITY_LIGHTS[rnd.nextInt(CITY_LIGHTS.length)], 0.45f + rnd.nextFloat() * 0.4f);
                }
            }
        }
        p.setShader(null);

        int[] px = new int[SW * SH];
        small.getPixels(px, 0, SW, 0, 0, SW, SH);
        blur(px, 2, 2);
        int[] sharp = px.clone();   // what the drops show
        if (wet(kind)) {
            blur(px, 5, 2);         // the frosted glass
            mix(px, night ? 0xFF1A2638 : 0xFFEEF2F6, night ? 0.1f : 0.3f);
        }
        small.setPixels(px, 0, SW, 0, 0, SW, SH);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(small, null, new Rect(0, 0, w, h), p);
        grain(out, wet(kind) ? (night ? 6f : 4.5f) : 1.5f, rnd);

        if (night && (kind == CLEAR || kind == PARTLY)) stars(c, p, rnd, w, h, k, kind == CLEAR ? 220 : 90);
        if (wet(kind)) {
            small.setPixels(sharp, 0, SW, 0, 0, SW, SH);
            glass(c, p, small, rnd, w, h, k, night, kind == DRIZZLE ? 60 : kind == RAIN ? 85 : 110);
        }
        small.recycle();
        return new BitmapShader(Backdrop.gpu(out), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int color, float a) {
        p.setShader(new RadialGradient(x, y, r, new int[] {Ui.alpha(color, a), Ui.alpha(color, a * 0.35f), 0},
                new float[] {0, 0.45f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    /** Soft clouds: clusters of round puffs with a flatter bottom, across a band of the sky (y0 to y1, of its height). */
    private static void clouds(Canvas c, Paint p, Random rnd, int count, int color, float a, float y0, float y1) {
        for (int i = 0; i < count; i++) {
            float cx = SW * (rnd.nextFloat() * 1.2f - 0.1f), cy = SH * (y0 + rnd.nextFloat() * (y1 - y0));
            float size = SW * (0.12f + rnd.nextFloat() * 0.12f);
            for (int j = 0; j < 8; j++) {
                float dx = (rnd.nextFloat() - 0.5f) * size * 1.8f, dy = -rnd.nextFloat() * size * 0.45f;
                float r = size * (0.35f + rnd.nextFloat() * 0.35f) * (1 - Math.abs(dx) / (size * 1.6f));
                glow(c, p, cx + dx, cy + dy, Math.max(4, r), color, a);
            }
        }
    }

    private static void stars(Canvas c, Paint p, Random rnd, int w, int h, float k, int count) {
        p.setShader(null);
        for (int i = 0; i < count; i++) {
            float x = rnd.nextFloat() * w, y = (float) Math.pow(rnd.nextFloat(), 1.6) * h * 0.75f;
            if (quiet(x / w, y / h) && rnd.nextFloat() < 0.5f) continue;
            float r = (0.6f + (float) Math.pow(rnd.nextFloat(), 3) * 1.8f) * k;
            p.setColor(Ui.alpha(0xFFFFFFFF, 0.35f + rnd.nextFloat() * 0.6f));
            c.drawCircle(x, y, r, p);
        }
    }

    /** Where the clock, the date and the weather sit (left, middle): little detail there. */
    private static boolean quiet(float x, float y) {
        return x < 0.42f && y > 0.24f && y < 0.7f;
    }

    // MARK: - The rainy window

    private static void glass(Canvas c, Paint p, Bitmap sharp, Random rnd, int w, int h, float k, boolean night,
                              int drops) {
        for (int i = 0; i < 450; i++) {
            float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h;
            if (quiet(x / w, y / h) && rnd.nextFloat() < 0.6f) continue;
            mist(c, p, x, y, (1 + rnd.nextFloat() * 1.6f) * k, night);
        }
        float[] sizes = new float[drops];
        for (int i = 0; i < sizes.length; i++) sizes[i] = (3.5f + (float) Math.pow(rnd.nextFloat(), 2.4) * 15) * k;
        java.util.Arrays.sort(sizes);
        BitmapShader scene = new BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        for (float r : sizes) {
            float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h;
            if (quiet(x / w, y / h) && rnd.nextFloat() < 0.85f) continue;
            if (r > 10 * k && rnd.nextFloat() < 0.4f) trail(c, p, x, y, r, (80 + rnd.nextFloat() * 240) * k, night, rnd);
            drop(c, p, scene, m, x, y, r, w, night);
        }
        p.setShader(null);
    }

    /**
     * A drop: a lens showing the sky behind it, sharper and flipped (a region about 5 drops wide squeezed into it),
     * darker at its edge where it bends the light away, a soft bright spot at the bottom where it focuses it, and a
     * small highlight at the top.
     */
    private static void drop(Canvas c, Paint p, BitmapShader scene, Matrix m, float x, float y, float r, int w,
                             boolean night) {
        float ry = r * 1.06f, s = (float) SW / w, zoom = 0.4f / s;
        p.setShader(null);
        p.setColor(Ui.alpha(0xFF000000, night ? 0.22f : 0.07f));
        c.drawOval(x - r, y - ry + r * 0.18f, x + r, y + ry + r * 0.18f, p);
        m.setTranslate(-x * s, -y * s);
        m.postScale(zoom, -zoom);
        m.postTranslate(x, y);
        scene.setLocalMatrix(m);
        p.setShader(scene);
        c.drawOval(x - r, y - ry, x + r, y + ry, p);
        p.setShader(new RadialGradient(x, y - ry * 0.1f, r * 1.05f,
                new int[] {0, 0, Ui.alpha(0xFF06101C, night ? 0.55f : 0.32f)},
                new float[] {0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawOval(x - r, y - ry, x + r, y + ry, p);
        p.setShader(new RadialGradient(x, y + ry * 0.55f, r * 0.55f,
                new int[] {Ui.alpha(0xFFFFFFFF, night ? 0.35f : 0.5f), 0}, null, Shader.TileMode.CLAMP));
        c.drawOval(x - r * 0.7f, y, x + r * 0.7f, y + ry, p);
        p.setShader(null);
        p.setColor(Ui.alpha(0xFFFFFFFF, night ? 0.8f : 0.9f));
        c.drawOval(x - r * 0.42f, y - ry * 0.62f, x - r * 0.12f, y - ry * 0.36f, p);
    }

    /** The clear line a drop wipes in the frost as it runs down, with a few beads left behind. */
    private static void trail(Canvas c, Paint p, float x, float y, float r, float len, boolean night, Random rnd) {
        float tw = r * 0.5f;
        p.setShader(new LinearGradient(0, y - len, 0, y,
                new int[] {0, Ui.alpha(night ? 0xFF000000 : 0xFFFFFFFF, night ? 0.12f : 0.22f)}, null,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x - tw / 2, y - len, x + tw / 2, y), tw / 2, tw / 2, p);
        p.setShader(null);
        for (float by = y - len * 0.9f; by < y - r * 2.5f; by += len * (0.18f + rnd.nextFloat() * 0.2f)) {
            mist(c, p, x + (rnd.nextFloat() - 0.5f) * tw, by, r * (0.18f + rnd.nextFloat() * 0.15f), night);
        }
    }

    /** A tiny bead: a dot with a light top and a darker bottom. */
    private static void mist(Canvas c, Paint p, float x, float y, float r, boolean night) {
        p.setShader(null);
        p.setColor(Ui.alpha(0xFF06101C, night ? 0.3f : 0.12f));
        c.drawCircle(x, y + r * 0.3f, r, p);
        p.setColor(Ui.alpha(0xFFFFFFFF, night ? 0.35f : 0.65f));
        c.drawCircle(x, y - r * 0.15f, r * 0.75f, p);
    }

    // MARK: - Pixels

    /** Blends every pixel toward a color. */
    private static void mix(int[] px, int color, float amount) {
        float keep = 1 - amount;
        for (int i = 0; i < px.length; i++) {
            int q = px[i], v = 0xFF000000;
            for (int s = 0; s < 24; s += 8) v |= Math.round(((q >>> s) & 255) * keep + ((color >>> s) & 255) * amount) << s;
            px[i] = v;
        }
    }

    /** Grain (the frosted glass's specks, when strong) and a Bayer dither, so the scaled-up sky doesn't band. */
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

    /** Box blur of radius r, passes times in each direction (repeating the edge pixels). */
    private static void blur(int[] px, int r, int passes) {
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < passes; pass++) {
            box(px, tmp, r, true);
            box(tmp, px, r, false);
        }
    }

    private static void box(int[] in, int[] out, int r, boolean horizontal) {
        int n = horizontal ? SW : SH, lines = horizontal ? SH : SW, step = horizontal ? 1 : SW, lineStep = horizontal ? SW : 1;
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

    // MARK: - What moves

    /**
     * Falling rain (thin slanted streaks), snow (drifting flakes) and lightning, drawn over the sky. Everything is
     * allocated up front: draw() runs on every frame and only moves numbers and makes one drawLines call.
     */
    static final class Precip {
        private static final int MAX = 220;
        private final float[] x = new float[MAX], y = new float[MAX], v = new float[MAX], s = new float[MAX];
        private final float[] lines = new float[MAX * 4];
        private final Random rnd = new Random();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int kind = NONE, n;
        private float w, h, k, len;
        private long last, flashAt, nextFlash;

        void set(int kind, int width, int height) {
            this.kind = kind;
            w = width;
            h = height;
            k = h / 1080f;
            n = kind == DRIZZLE ? 70 : kind == RAIN ? 130 : kind == HEAVY || kind == STORM ? MAX : kind == SNOW ? 120 : 0;
            len = (kind == DRIZZLE ? 16 : kind == RAIN ? 30 : 44) * k;
            for (int i = 0; i < n; i++) {
                x[i] = rnd.nextFloat() * w * 1.1f;
                y[i] = rnd.nextFloat() * h;
                s[i] = 0.6f + rnd.nextFloat() * 0.8f;   // depth: nearer ones are bigger, faster and brighter
                v[i] = (kind == SNOW ? 50 + 70 * s[i] : (kind == DRIZZLE ? 650 : 1250) * s[i]) * k;
            }
            last = 0;
            nextFlash = android.os.SystemClock.uptimeMillis() + 4000;
        }

        boolean active() {
            return n > 0;
        }

        void draw(Canvas c, long now, float night) {
            if (n == 0) return;
            float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
            last = now;
            if (kind == SNOW) {
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < n; i++) {
                    y[i] += v[i] * dt;
                    x[i] += (float) Math.sin(now / 900.0 * s[i] + i) * 18 * k * dt;
                    if (y[i] > h + 8 * k) {
                        y[i] = -8 * k;
                        x[i] = rnd.nextFloat() * w;
                    }
                    paint.setColor(Ui.alpha(0xFFFFFFFF, 0.5f + 0.4f * (s[i] - 0.6f) / 0.8f));
                    c.drawCircle(x[i], y[i], (1.4f + 2.6f * (s[i] - 0.6f) / 0.8f) * k, paint);
                }
                return;
            }
            float slant = 0.14f;
            for (int i = 0; i < n; i++) {
                y[i] += v[i] * dt;
                x[i] -= v[i] * dt * slant;
                float l = len * s[i];
                if (y[i] - l > h) {
                    y[i] = -rnd.nextFloat() * h * 0.15f;
                    x[i] = rnd.nextFloat() * w * 1.1f;
                }
                lines[i * 4] = x[i];
                lines[i * 4 + 1] = y[i];
                lines[i * 4 + 2] = x[i] + l * slant;
                lines[i * 4 + 3] = y[i] - l;
            }
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth((kind == DRIZZLE ? 1.4f : 1.8f) * k);
            paint.setColor(Ui.alpha(0xFFFFFFFF, 0.42f - 0.12f * night));
            c.drawLines(lines, 0, n * 4, paint);
            if (kind == STORM) flash(c, now, night);
        }

        /** Lightning every 7 to 16 seconds: a bright flicker, a second one, then a fade. */
        private void flash(Canvas c, long now, float night) {
            if (now >= nextFlash) {
                flashAt = now;
                nextFlash = now + 7000 + rnd.nextInt(9000);
            }
            long t = now - flashAt;
            float a = t < 70 ? 1 : t < 150 ? 0.15f : t < 240 ? 0.75f : t < 900 ? 0.75f * (1 - (t - 240) / 660f) : 0;
            if (a > 0) c.drawColor(Ui.alpha(0xFFFFFFFF, a * (0.3f + 0.25f * night)));
        }
    }
}
