package com.washed.mapcanvas;

import java.awt.image.BufferedImage;

public class Dithering {

    private static final int[] MAP_RED = new int[256];
    private static final int[] MAP_GREEN = new int[256];
    private static final int[] MAP_BLUE = new int[256];
    private static final boolean[] VALID_MAP_COLOR = new boolean[256];

    static {
        for (int i = 4; i < 256; i++) {
            int base = i >> 2;
            if (base >= MapColorPalette.COLORS.length) continue;
            int[] rgb = MapColorPalette.COLORS[base];
            float multiplier = MapColorPalette.MULTIPLIERS[i & 3];
            MAP_RED[i] = (int) (rgb[0] * multiplier);
            MAP_GREEN[i] = (int) (rgb[1] * multiplier);
            MAP_BLUE[i] = (int) (rgb[2] * multiplier);
            VALID_MAP_COLOR[i] = true;
        }
    }

    public static byte[] ditherToMapColors(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int size = w * h;

        // Flat row-major buffers avoid thousands of small row-array objects and
        // keep the pixel walk and Floyd-Steinberg writes cache-friendly.
        float[] r = new float[size];
        float[] g = new float[size];
        float[] b = new float[size];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                int rgb = image.getRGB(x, y);
                r[index] = (rgb >> 16) & 0xFF;
                g[index] = (rgb >> 8) & 0xFF;
                b[index] = rgb & 0xFF;
            }
        }

        byte[] result = new byte[size];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                int ri = clamp((int) r[index]);
                int gi = clamp((int) g[index]);
                int bi = clamp((int) b[index]);

                byte mapColor = closestMapColor(ri, gi, bi);
                result[index] = mapColor;

                int mapIndex = mapColor & 0xFF;
                float errR = (ri - MAP_RED[mapIndex]) * 0.3f;
                float errG = (gi - MAP_GREEN[mapIndex]) * 0.3f;
                float errB = (bi - MAP_BLUE[mapIndex]) * 0.3f;

                if (x + 1 < w) {
                    r[index + 1] += errR * 7f / 16f;
                    g[index + 1] += errG * 7f / 16f;
                    b[index + 1] += errB * 7f / 16f;
                }
                if (x > 0 && y + 1 < h) {
                    r[index + w - 1] += errR * 3f / 16f;
                    g[index + w - 1] += errG * 3f / 16f;
                    b[index + w - 1] += errB * 3f / 16f;
                }
                if (y + 1 < h) {
                    r[index + w] += errR * 5f / 16f;
                    g[index + w] += errG * 5f / 16f;
                    b[index + w] += errB * 5f / 16f;
                }
                if (x + 1 < w && y + 1 < h) {
                    r[index + w + 1] += errR / 16f;
                    g[index + w + 1] += errG / 16f;
                    b[index + w + 1] += errB / 16f;
                }
            }
        }

        return result;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static byte closestMapColor(int r, int g, int b) {
        byte closest = 4;
        int closestDist = Integer.MAX_VALUE;
        for (int i = 4; i < 256; i++) {
            if (!VALID_MAP_COLOR[i]) continue;
            int dr = r - MAP_RED[i];
            int dg = g - MAP_GREEN[i];
            int db = b - MAP_BLUE[i];
            int dist = dr * dr + dg * dg + db * db;
            if (dist < closestDist) {
                closestDist = dist;
                closest = (byte) i;
            }
        }
        return closest;
    }
}
