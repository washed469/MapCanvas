package com.washed.mapcanvas;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

public class ImageProcessor {

    public static BufferedImage downloadImage(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "image/gif,image/webp,image/*,*/*");
        String host = URI.create(url).getHost();
        if (host != null && (host.endsWith("discordapp.com") || host.endsWith("discordapp.net"))) {
            conn.setRequestProperty("Referer", "https://discord.com/");
        } else if (host != null && host.endsWith("klipy.com")) {
            conn.setRequestProperty("Referer", "https://klipy.com/");
        }
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        try {
            int status = conn.getResponseCode();
            String finalHost = conn.getURL().getHost();
            String contentType = conn.getContentType();
            if (status < 200 || status >= 300) {
                MapCanvasDebug.log("image-download-http-failure requestedHost=" + safeHost(url)
                        + " finalHost=" + (finalHost == null ? "(unknown)" : finalHost)
                        + " status=" + status + " contentType=" + safeValue(contentType)
                        + " contentLength=" + conn.getContentLengthLong());
                throw new IOException("Image host " + safeHost(url) + " returned HTTP " + status
                        + (contentType == null ? "" : " (" + contentType + ")"));
            }
            try (java.io.InputStream input = conn.getInputStream()) {
                BufferedImage img = ImageIO.read(input);
                if (img == null) {
                    MapCanvasDebug.log("image-download-not-image requestedHost=" + safeHost(url)
                            + " finalHost=" + (finalHost == null ? "(unknown)" : finalHost)
                            + " status=" + status + " contentType=" + safeValue(contentType));
                    throw new IOException("URL returned HTTP " + status + " but its response is not a supported image ("
                            + contentType + ")");
                }
                MapCanvasDebug.log("image-download-ok requestedHost=" + safeHost(url)
                        + " finalHost=" + (finalHost == null ? "(unknown)" : finalHost)
                        + " status=" + status + " contentType=" + safeValue(contentType)
                        + " format=" + imageFormat(contentType, finalHost)
                        + " dimensions=" + img.getWidth() + "x" + img.getHeight());
                return img;
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String safeHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "(unknown)" : host;
        } catch (IllegalArgumentException ignored) {
            return "(unknown)";
        }
    }

    private static String safeValue(String value) {
        return value == null ? "(unknown)" : value.replaceAll("[^A-Za-z0-9.+/_-]", "_");
    }

    private static String imageFormat(String contentType, String host) {
        String value = ((contentType == null ? "" : contentType) + " " + (host == null ? "" : host)).toLowerCase();
        if (value.contains("gif")) return "gif";
        if (value.contains("webp")) return "webp";
        if (value.contains("png")) return "png";
        if (value.contains("jpeg") || value.contains("jpg")) return "jpeg";
        return "unknown";
    }

    public static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    public static BufferedImage[] splitIntoMaps(BufferedImage image, int cols, int rows) {
        BufferedImage[] maps = new BufferedImage[cols * rows];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                maps[row * cols + col] = image.getSubimage(
                        col * 128, row * 128, 128, 128
                );
            }
        }
        return maps;
    }
}
