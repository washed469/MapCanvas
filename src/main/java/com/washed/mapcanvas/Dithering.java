package com.washed.mapcanvas;

import java.awt.image.BufferedImage;

public class Dithering {

    public static byte[] ditherToMapColors(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        float[][] r = new float[w][h];
        float[][] g = new float[w][h];
        float[][] b = new float[w][h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rgb = image.getRGB(x, y);
                r[x][y] = (rgb >> 16) & 0xFF;
                g[x][y] = (rgb >> 8) & 0xFF;
                b[x][y] = rgb & 0xFF;
            }
        }

        byte[] result = new byte[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ri = clamp((int) r[x][y]);
                int gi = clamp((int) g[x][y]);
                int bi = clamp((int) b[x][y]);

                byte mapColor = closestMapColor(ri, gi, bi);
                result[y * w + x] = mapColor;

                int[] actual = getMapColorRgb(mapColor);
                float errR = (ri - actual[0]) * 0.3f;
                float errG = (gi - actual[1]) * 0.3f;
                float errB = (bi - actual[2]) * 0.3f;

                if (x + 1 < w) {
                    r[x+1][y] += errR * 7f / 16f;
                    g[x+1][y] += errG * 7f / 16f;
                    b[x+1][y] += errB * 7f / 16f;
                }
                if (x - 1 >= 0 && y + 1 < h) {
                    r[x-1][y+1] += errR * 3f / 16f;
                    g[x-1][y+1] += errG * 3f / 16f;
                    b[x-1][y+1] += errB * 3f / 16f;
                }
                if (y + 1 < h) {
                    r[x][y+1] += errR * 5f / 16f;
                    g[x][y+1] += errG * 5f / 16f;
                    b[x][y+1] += errB * 5f / 16f;
                }
                if (x + 1 < w && y + 1 < h) {
                    r[x+1][y+1] += errR * 1f / 16f;
                    g[x+1][y+1] += errG * 1f / 16f;
                    b[x+1][y+1] += errB * 1f / 16f;
                }
            }
        }

        return result;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int[] getMapColorRgb(byte mapColor) {
        int i = mapColor & 0xFF;
        int base = i >> 2;
        int shade = i & 3;
        if (base >= MapColorPalette.COLORS.length) return new int[]{0, 0, 0};
        int[] baseColor = MapColorPalette.COLORS[base];
        float multiplier = MapColorPalette.MULTIPLIERS[shade];
        return new int[]{
                (int)(baseColor[0] * multiplier),
                (int)(baseColor[1] * multiplier),
                (int)(baseColor[2] * multiplier)
        };
    }

    private static byte closestMapColor(int r, int g, int b) {
        byte closest = 4;
        double closestDist = Double.MAX_VALUE;
        for (int i = 4; i < 256; i++) {
            int base = i >> 2;
            int shade = i & 3;
            if (base >= MapColorPalette.COLORS.length) continue;
            int[] baseColor = MapColorPalette.COLORS[base];
            float multiplier = MapColorPalette.MULTIPLIERS[shade];
            int mr = (int)(baseColor[0] * multiplier);
            int mg = (int)(baseColor[1] * multiplier);
            int mb = (int)(baseColor[2] * multiplier);
            double dist = Math.pow(r - mr, 2) + Math.pow(g - mg, 2) + Math.pow(b - mb, 2);
            if (dist < closestDist) {
                closestDist = dist;
                closest = (byte) i;
            }
        }
        return closest;
    }
}