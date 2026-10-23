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

    /**
     * The text on the sky is white: like Apple Weather's, every sky (clear, rain, fog, dusk, night) is deep enough for
     * it. (Kept as a question: a lighter palette would answer false and get dark text.)
     */
    static boolean blue(int kind) {
        return kind != NONE;
    }

    private static boolean wet(int kind) {
        return kind == DRIZZLE || kind == RAIN || kind == HEAVY || kind == STORM;
    }

    /** The three looks of a sky: by day, at dusk (and dawn), at night. */
    static final int DAY = 0, DUSK = 1, NIGHT = 2;

    /** Around sunset (70 minutes before to 50 after) and sunrise (40 before to 50 after): the evening's gradient. */
    static boolean dusk(HomeData.Weather wx, long now) {
        if (wx == null || wx.sunset == 0) return false;
        long m = 60_000L;
        return (now > wx.sunset - 70 * m && now < wx.sunset + 50 * m)
                || (wx.sunrise != 0 && now > wx.sunrise - 40 * m && now < wx.sunrise + 50 * m);
    }

    /**
     * The sky's colors, top to bottom, after Apple Weather: a deep blue fading lighter at the horizon by day, deep
     * blue through lavender to peach at dusk, navy-violet at night; slate for rain, gray-blue for clouds and fog.
     */
    private static int[] colors(int kind, int look) {
        if (look == DUSK) {
            switch (kind) {
                case CLEAR: case PARTLY: return new int[] {0xFF26356A, 0xFF4C5490, 0xFF8A7EA9, 0xFFD3A38E};
                case CLOUDY:  return new int[] {0xFF353C5C, 0xFF5E5F82, 0xFF8C7F95, 0xFFB98F8A};
                case FOG:     return new int[] {0xFF555B74, 0xFF7B7891, 0xFFA295A3, 0xFFC4A6A2};
                case SNOW:    return new int[] {0xFF485274, 0xFF6F7299, 0xFF9C93B2, 0xFFC7A9AF};
                case STORM:   return new int[] {0xFF20263A, 0xFF353A52, 0xFF4E4D63, 0xFF6A5F6C};
                default:      return new int[] {0xFF303750, 0xFF474E68, 0xFF625F79, 0xFF85737F};    // rain
            }
        }
        if (look == NIGHT) {
            switch (kind) {
                case CLEAR:   return new int[] {0xFF0A0F27, 0xFF171D40, 0xFF2A305E};
                case PARTLY:  return new int[] {0xFF0E1433, 0xFF1D2350, 0xFF323868};
                case CLOUDY:  return new int[] {0xFF161A2E, 0xFF242942, 0xFF353A57};
                case FOG:     return new int[] {0xFF20242D, 0xFF2E333D, 0xFF40454F};
                case SNOW:    return new int[] {0xFF1A2236, 0xFF2A344E, 0xFF3D4965};
                case STORM:   return new int[] {0xFF0C101C, 0xFF171C2C, 0xFF232A3B};
                default:      return new int[] {0xFF121826, 0xFF1D2536, 0xFF2B3446};    // rain
            }
        }
        switch (kind) {
            case CLEAR:   return new int[] {0xFF2C5BA4, 0xFF4A81C3, 0xFF9DC1E5};
            case PARTLY:  return new int[] {0xFF3762A4, 0xFF5889C2, 0xFFA2BFDD};
            case CLOUDY:  return new int[] {0xFF4B5E76, 0xFF6C8097, 0xFF9AACBE};
            case FOG:     return new int[] {0xFF636E7C, 0xFF85909E, 0xFFAEB7C1};
            case DRIZZLE: return new int[] {0xFF465469, 0xFF5D6B80, 0xFF7C899B};
            case RAIN:    return new int[] {0xFF3A4557, 0xFF4F5B6E, 0xFF6B7788};
            case HEAVY:   return new int[] {0xFF303A4A, 0xFF445062, 0xFF5D697A};
            case SNOW:    return new int[] {0xFF5A6D87, 0xFF7D90A9, 0xFFAEBED0};
            default:      return new int[] {0xFF242C39, 0xFF374152, 0xFF4E5969};    // storm
        }
    }

    /**
     * The glass on this sky (Scene's tint): the sky's own color a shade deeper, as Apple's cards; drawn at about 45%
     * over the frosted sky.
     */
    static int glass(int kind, int look) {
        int top = colors(kind, look)[0];
        int r = Math.round(((top >> 16) & 255) * 0.85f), g = Math.round(((top >> 8) & 255) * 0.85f),
                b = Math.round((top & 255) * 0.9f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The sky is drawn this small: blurring it is cheap, and it's all soft shapes anyway. */
    private static final int SW = 320, SH = 180;

    private static final int[] CITY_LIGHTS = {0xFFFFA94D, 0xFFFF5E5B, 0xFF4FD1C5, 0xFFFFE8C2, 0xFF5B8CFF, 0xFFFFC857};

    /** Builds the sky (slow: call it off the UI thread). */
    static Shader make(int width, int height, int kind, int look) {
        int w = Math.max(2, width), h = Math.max(2, height);
        Random rnd = new Random(kind * 31 + look * 7 + 3);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        return finish(paint(kind, look, rnd), p, rnd, w, h, h / 1080f, kind, look);
    }

    /**
     * The screensaver's background: the same sky behind heavy frosted glass, dimmed for a dark room (a little less at
     * night, when the sky is dark already). Slow: call it off the UI thread.
     */
    static Bitmap frosted(int width, int height, int kind, int look) {
        int w = Math.max(2, width), h = Math.max(2, height);
        Random rnd = new Random(kind * 31 + look * 7 + 3);
        Bitmap small = paint(kind, look, rnd);
        int[] px = new int[SW * SH];
        small.getPixels(px, 0, SW, 0, 0, SW, SH);
        blur(px, 6, 3);
        mix(px, 0xFF05070D, look == NIGHT ? 0.08f : 0.15f);
        small.setPixels(px, 0, SW, 0, 0, SW, SH);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(small, null, new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        small.recycle();
        grain(out, 5f, rnd);
        return Backdrop.gpu(out);
    }

    /** The sky blurred hard, small (SW × SH): what the glass shows on Android 8 to 12 (Scene.setLegacyFrost). */
    static Bitmap frost(int kind, int look) {
        Random rnd = new Random(kind * 31 + look * 7 + 3);
        Bitmap small = paint(kind, look, rnd);
        int[] px = new int[SW * SH];
        small.getPixels(px, 0, SW, 0, 0, SW, SH);
        blur(px, 6, 3);
        small.setPixels(px, 0, SW, 0, 0, SW, SH);
        return small;
    }

    /**
     * The sky itself, small (SW × SH): the gradient, the sun's glow (low and warm at dusk), cirrus wisps on a clear
     * sky, clouds, fog, city lights in the rain at night.
     */
    private static Bitmap paint(int kind, int look, Random rnd) {
        boolean night = look == NIGHT, dusk = look == DUSK;
        Bitmap small = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        int[] sky = colors(kind, look);
        p.setShader(new LinearGradient(0, 0, 0, SH, sky, null, Shader.TileMode.CLAMP));
        sc.drawRect(0, 0, SW, SH, p);
        // Clouds: white by day, lavender-rose at dusk, lavender-gray at night (as Apple's).
        int cloud = dusk ? 0xFFB9A3C6 : night ? 0xFF474A75 : 0xFFFFFFFF;

        if (kind == CLEAR || kind == PARTLY) {
            if (dusk) {
                glow(sc, p, SW * 0.7f, SH * 1.02f, SW * 0.6f, 0xFFFFC9A2, 0.45f);  // the sun just under the horizon
            } else if (!night) {
                glow(sc, p, SW * 0.84f, SH * 0.06f, SW * 0.5f, 0xFFFFF4DC, 0.5f);
                glow(sc, p, SW * 0.84f, SH * 0.06f, SW * 0.1f, 0xFFFFFFFF, 0.8f);
            }
            cirrus(sc, p, rnd, night ? 0xFF8A8FC0 : dusk ? 0xFFE8D4E4 : 0xFFFFFFFF, night ? 0.14f : 0.26f);
        }
        if (kind == PARTLY) clouds(sc, p, rnd, 4, cloud, night ? 0.75f : 0.8f, 0.05f, 0.45f);
        if (kind == CLOUDY) {
            clouds(sc, p, rnd, 5, night ? 0xFF2A2F48 : dusk ? 0xFF6E6788 : 0xFF8597AA, 0.8f, -0.05f, 0.35f);
            clouds(sc, p, rnd, 6, cloud, night ? 0.55f : 0.6f, 0.1f, 0.7f);
        }
        if (kind == SNOW) clouds(sc, p, rnd, 6, cloud, 0.5f, -0.05f, 0.4f);
        if (kind == STORM) {
            clouds(sc, p, rnd, 7, night ? 0xFF161A28 : 0xFF2A303C, 0.85f, -0.08f, 0.4f);
            clouds(sc, p, rnd, 4, night ? 0xFF2A3044 : 0xFF566170, 0.6f, 0.15f, 0.6f);
        }
        if (kind == FOG) {
            for (int i = 0; i < 7; i++) {
                float y = SH * (0.2f + i * 0.12f + rnd.nextFloat() * 0.05f);
                p.setShader(new LinearGradient(0, y - SH * 0.08f, 0, y + SH * 0.08f,
                        new int[] {0, Ui.alpha(night ? 0xFF5A626E : 0xFFD5DAE0, 0.4f), 0}, null, Shader.TileMode.CLAMP));
                sc.drawRect(0, y - SH * 0.08f, SW, y + SH * 0.08f, p);
            }
        }
        if (wet(kind)) {
            clouds(sc, p, rnd, 5, night ? 0xFF2A3548 : dusk ? 0xFF5E5A72 : 0xFF6F7B8C, 0.55f, -0.05f, 0.5f);
            if (night) {
                for (int i = 0; i < 40; i++) {
                    float x = rnd.nextFloat() * SW, y = SH * (0.35f + rnd.nextFloat() * 0.65f), r = 3 + rnd.nextFloat() * 11;
                    glow(sc, p, x, y, r, CITY_LIGHTS[rnd.nextInt(CITY_LIGHTS.length)], 0.45f + rnd.nextFloat() * 0.4f);
                }
            }
        }
        p.setShader(null);
        return small;
    }

    /** Softens the small sky, frosts it when wet, scales it up with grain, and adds stars or the drops on the glass. */
    private static Shader finish(Bitmap small, Paint p, Random rnd, int w, int h, float k, int kind, int look) {
        boolean night = look == NIGHT;
        int[] px = new int[SW * SH];
        small.getPixels(px, 0, SW, 0, 0, SW, SH);
        blur(px, 2, 2);
        int[] sharp = px.clone();   // what the drops show
        if (wet(kind)) {
            blur(px, 5, 2);         // the frosted glass
            mix(px, night ? 0xFF1A2638 : 0xFF8E9AAA, night ? 0.1f : 0.12f);
        }
        small.setPixels(px, 0, SW, 0, 0, SW, SH);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(small, null, new Rect(0, 0, w, h), p);
        grain(out, wet(kind) ? (night ? 6f : 4.5f) : 1.5f, rnd);

        if (night && (kind == CLEAR || kind == PARTLY)) stars(c, p, rnd, w, h, k, kind == CLEAR ? 220 : 90);
        // The moon at dusk and at night, high on the left (above the clock), as Apple's: when the sky is clear enough.
        if (look != DAY && (kind == CLEAR || kind == PARTLY || kind == CLOUDY)) moon(c, p, w * 0.075f, h * 0.13f, 46 * k, rnd);
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

    /** Long thin streaks of high cloud, slightly tilted, in the upper sky (softened by the blur that follows). */
    private static void cirrus(Canvas c, Paint p, Random rnd, int color, float a) {
        p.setShader(null);
        for (int i = 0; i < 16; i++) {
            float cx = rnd.nextFloat() * SW * 1.1f - SW * 0.05f, cy = SH * (0.04f + rnd.nextFloat() * 0.55f);
            float len = SW * (0.12f + rnd.nextFloat() * 0.3f), thick = 1.2f + rnd.nextFloat() * 2.4f;
            c.save();
            c.rotate(-8 - rnd.nextFloat() * 8, cx, cy);
            p.setColor(Ui.alpha(color, a * (0.5f + rnd.nextFloat() * 0.5f)));
            c.drawOval(cx - len / 2, cy - thick / 2, cx + len / 2, cy + thick / 2, p);
            // A fainter, wider veil along each streak.
            p.setColor(Ui.alpha(color, a * 0.25f));
            c.drawOval(cx - len * 0.6f, cy - thick * 2, cx + len * 0.6f, cy + thick * 2, p);
            c.restore();
        }
    }

    /** A soft gray-white moon, lit from the top left, its edge melting into a faint halo. */
    private static void moon(Canvas c, Paint p, float x, float y, float r, Random rnd) {
        p.setShader(new RadialGradient(x, y, r * 2.8f, new int[] {Ui.alpha(0xFFE6E8F2, 0.2f), Ui.alpha(0xFFE6E8F2, 0.06f), 0},
                new float[] {0, 0.45f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 2.8f, p);
        p.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.4f,
                new int[] {0xFFF4F4F7, 0xFFD9DBE3, 0xFFAEB2C0}, new float[] {0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
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
