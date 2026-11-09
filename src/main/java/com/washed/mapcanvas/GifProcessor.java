package com.washed.mapcanvas;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class GifProcessor {

    public static final int MAX_FPS = 10;
    public static final int MIN_FRAME_DELAY_MS = 1000 / MAX_FPS;

    public record GifData(byte[][] frames, int[] delaysMs, int width, int height) {}

    public static boolean isGifUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            String query = uri.getQuery();
            return (path != null && path.toLowerCase().endsWith(".gif"))
                    || (query != null && query.toLowerCase().matches(".*(?:^|&)format=gif(?:&.*|$).*"));
        } catch (IllegalArgumentException e) {
            return url.toLowerCase().contains(".gif");
        }
    }

    public static GifData processGif(String url, int cols, int rows) throws IOException {

        java.net.HttpURLConnection gifConn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
        gifConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        gifConn.setRequestProperty("Accept", "image/gif,image/webp,image/*,*/*");
        String host = URI.create(url).getHost();
        if (host != null && (host.endsWith("discordapp.com") || host.endsWith("discordapp.net"))) {
            gifConn.setRequestProperty("Referer", "https://discord.com/");
        } else if (host != null && host.endsWith("klipy.com")) {
            gifConn.setRequestProperty("Referer", "https://klipy.com/");
        }
        gifConn.setConnectTimeout(10000);
        gifConn.setReadTimeout(30000);
        gifConn.setInstanceFollowRedirects(true);
        int status;
        try {
            status = gifConn.getResponseCode();
        } catch (IOException e) {
            MapCanvasDebug.log("gif-download-connection-failure requestedHost=" + safeHost(url)
                    + " error=" + safeValue(e.getClass().getSimpleName() + ":" + e.getMessage()));
            gifConn.disconnect();
            throw e;
        }
        String finalHost = gifConn.getURL().getHost();
        String contentType = gifConn.getContentType();
        if (status < 200 || status >= 300) {
            long contentLength = gifConn.getContentLengthLong();
            gifConn.disconnect();
            MapCanvasDebug.log("gif-download-http-failure requestedHost=" + safeHost(url)
                    + " finalHost=" + safeHost(finalHost) + " status=" + status
                    + " contentType=" + safeValue(contentType) + " contentLength=" + contentLength);
            throw new IOException("GIF host " + safeHost(url) + " returned HTTP " + status
                    + (contentType == null ? "" : " (" + contentType + ")"));
        }
        ImageInputStream stream = ImageIO.createImageInputStream(gifConn.getInputStream());
        if (stream == null) {
            gifConn.disconnect();
            MapCanvasDebug.log("gif-download-no-image-stream requestedHost=" + safeHost(url)
                    + " finalHost=" + safeHost(finalHost) + " status=" + status
                    + " contentType=" + safeValue(contentType));
            throw new IOException("GIF response could not be opened as an image stream (" + contentType + ")");
        }
        java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) {
            stream.close();
            gifConn.disconnect();
            MapCanvasDebug.log("gif-download-unsupported-format requestedHost=" + safeHost(url)
                    + " finalHost=" + safeHost(finalHost) + " status=" + status
                    + " contentType=" + safeValue(contentType));
            throw new IOException("Response is not a supported animated image (" + contentType + ")");
        }
        ImageReader reader = readers.next();
        String formatName = reader.getFormatName();
        if (!"gif".equalsIgnoreCase(formatName)) {
            reader.dispose();
            stream.close();
            gifConn.disconnect();
            MapCanvasDebug.log("gif-download-wrong-format requestedHost=" + safeHost(url)
                    + " finalHost=" + safeHost(finalHost) + " status=" + status
                    + " contentType=" + safeValue(contentType) + " imageFormat=" + safeValue(formatName));
            throw new IOException("URL returned " + formatName + " instead of an animated GIF (" + contentType + ")");
        }
        reader.setInput(stream);

        int frameCount = reader.getNumImages(true);
        if (frameCount < 1) {
            reader.dispose();
            stream.close();
            gifConn.disconnect();
            MapCanvasDebug.log("gif-download-no-frames requestedHost=" + safeHost(url)
                    + " finalHost=" + safeHost(finalHost) + " status=" + status
                    + " contentType=" + safeValue(contentType));
            throw new IOException("GIF response contains no frames");
        }
        MapCanvasDebug.log("gif-download-ok requestedHost=" + safeHost(url)
                + " finalHost=" + safeHost(finalHost) + " status=" + status
                + " contentType=" + safeValue(contentType) + " frames=" + frameCount);
        int totalWidth = cols * 128;
        int totalHeight = rows * 128;

        int[] delays = new int[frameCount];
        byte[][] framesArray = new byte[frameCount][];
        int workerCount = Math.max(1, Math.min(4, Math.min(Runtime.getRuntime().availableProcessors(), frameCount)));
        int maxPendingFrames = workerCount * 2;
        ExecutorService workers = Executors.newFixedThreadPool(workerCount);
        List<Future<?>> pending = new ArrayList<>(maxPendingFrames);
        BufferedImage canvas = null;
        BufferedImage previousCanvas = null;

        int[] canvasSize = extractCanvasSize(reader);
        int canvasWidth = canvasSize[0];
        int canvasHeight = canvasSize[1];

        try {
        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = reader.read(i);
            IIOMetadata meta = reader.getImageMetadata(i);

            int delayMs = extractDelay(meta);
            if (delayMs < MIN_FRAME_DELAY_MS) delayMs = MIN_FRAME_DELAY_MS;
            delays[i] = delayMs;

            int disposalMethod = extractDisposalMethod(meta);
            int[] offset = extractFrameOffset(meta);
            int offsetX = offset[0];
            int offsetY = offset[1];

            if (canvas == null) {
                canvas = new BufferedImage(canvasWidth > 0 ? canvasWidth : frame.getWidth(),
                        canvasHeight > 0 ? canvasHeight : frame.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
            }

            if (disposalMethod == 3 && previousCanvas != null) {
                canvas = copyImage(previousCanvas);
            } else if (disposalMethod == 2) {
                canvas = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
            }

            previousCanvas = copyImage(canvas);

            java.awt.Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, offsetX, offsetY, null);
            g.dispose();

            BufferedImage resizedFrame = ImageProcessor.resizeImage(copyImage(canvas), totalWidth, totalHeight);
            int frameIndex = i;
            pending.add(workers.submit(() -> {
                BufferedImage[] sections = ImageProcessor.splitIntoMaps(resizedFrame, cols, rows);
                byte[] frameBytes = new byte[cols * rows * 128 * 128];
                for (int s = 0; s < sections.length; s++) {
                    byte[] mapBytes = imageToMapColors(sections[s]);
                    System.arraycopy(mapBytes, 0, frameBytes, s * 128 * 128, mapBytes.length);
                }
                framesArray[frameIndex] = frameBytes;
            }));

            // Bound the number of full-size decoded frames waiting for color
            // conversion. The old path retained every resized frame at once.
            if (pending.size() >= maxPendingFrames) await(pending.remove(0));
        }

        for (Future<?> future : pending) await(future);
        } finally {
            workers.shutdownNow();
            reader.dispose();
            stream.close();
            gifConn.disconnect();
        }

        return new GifData(framesArray, delays, cols, rows);
    }

    private static String safeHost(String host) {
        if (host == null || host.isBlank()) return "(unknown)";
        return host.replaceAll("[^A-Za-z0-9.-]", "_");
    }

    private static String safeValue(String value) {
        if (value == null) return "(unknown)";
        String sanitized = value.replaceAll("[^A-Za-z0-9.+/_-]", "_");
        return sanitized.length() > 160 ? sanitized.substring(0, 160) : sanitized;
    }

    private static void await(Future<?> future) throws IOException {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("GIF processing was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IOException("Could not convert GIF frame", cause);
        }
    }

    public static byte[] imageToMapColors(BufferedImage image) {
        return Dithering.ditherToMapColors(image);
    }

    private static int[] extractFrameOffset(IIOMetadata meta) {
        String metaFormat = meta.getNativeMetadataFormatName();
        org.w3c.dom.Node tree = meta.getAsTree(metaFormat);
        org.w3c.dom.NodeList children = tree.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if ("ImageDescriptor".equals(child.getNodeName())) {
                org.w3c.dom.NamedNodeMap attrs = child.getAttributes();
                int x = 0, y = 0;
                org.w3c.dom.Node xNode = attrs.getNamedItem("imageLeftPosition");
                org.w3c.dom.Node yNode = attrs.getNamedItem("imageTopPosition");
                if (xNode != null) x = Integer.parseInt(xNode.getNodeValue());
                if (yNode != null) y = Integer.parseInt(yNode.getNodeValue());
                return new int[]{x, y};
            }
        }
        return new int[]{0, 0};
    }

    private static int[] extractCanvasSize(ImageReader reader) {
        try {
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                String format = streamMeta.getNativeMetadataFormatName();
                org.w3c.dom.Node tree = streamMeta.getAsTree(format);
                org.w3c.dom.NodeList children = tree.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    org.w3c.dom.Node child = children.item(i);
                    if ("LogicalScreenDescriptor".equals(child.getNodeName())) {
                        org.w3c.dom.NamedNodeMap attrs = child.getAttributes();
                        int w = Integer.parseInt(attrs.getNamedItem("logicalScreenWidth").getNodeValue());
                        int h = Integer.parseInt(attrs.getNamedItem("logicalScreenHeight").getNodeValue());
                        return new int[]{w, h};
                    }
                }
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.createGraphics().drawImage(src, 0, 0, null);
        return copy;
    }

    private static int extractDisposalMethod(IIOMetadata meta) {
        String metaFormat = meta.getNativeMetadataFormatName();
        org.w3c.dom.Node tree = meta.getAsTree(metaFormat);
        org.w3c.dom.NodeList children = tree.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if ("GraphicControlExtension".equals(child.getNodeName())) {
                org.w3c.dom.NamedNodeMap attrs = child.getAttributes();
                org.w3c.dom.Node disposalNode = attrs.getNamedItem("disposalMethod");
                if (disposalNode != null) {
                    return switch (disposalNode.getNodeValue()) {
                        case "restoreToBackgroundColor" -> 2;
                        case "restoreToPrevious" -> 3;
                        default -> 1;
                    };
                }
            }
        }
        return 1;
    }

    private static int extractDelay(IIOMetadata meta) {
        String metaFormat = meta.getNativeMetadataFormatName();
        org.w3c.dom.Node tree = meta.getAsTree(metaFormat);
        org.w3c.dom.NodeList children = tree.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if ("GraphicControlExtension".equals(child.getNodeName())) {
                org.w3c.dom.NamedNodeMap attrs = child.getAttributes();
                org.w3c.dom.Node delayNode = attrs.getNamedItem("delayTime");
                if (delayNode != null) {
                    return Integer.parseInt(delayNode.getNodeValue()) * 10;
                }
            }
        }
        return MIN_FRAME_DELAY_MS;
    }
}
