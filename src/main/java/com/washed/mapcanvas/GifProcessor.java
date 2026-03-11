package com.washed.mapcanvas;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class GifProcessor {

    public static final int MAX_FPS = 10;
    public static final int MIN_FRAME_DELAY_MS = 1000 / MAX_FPS;

    public record GifData(byte[][] frames, int[] delaysMs, int width, int height) {}

    public static boolean isGifUrl(String url) {
        return url.toLowerCase().contains(".gif");
    }

    public static GifData processGif(String url, int cols, int rows) throws IOException {

        java.net.HttpURLConnection gifConn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
        gifConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        gifConn.setRequestProperty("Accept", "image/gif,image/webp,image/*,*/*");
        gifConn.setRequestProperty("Referer", "https://www.google.com/");
        gifConn.setConnectTimeout(10000);
        gifConn.setReadTimeout(30000);
        gifConn.setInstanceFollowRedirects(true);
        ImageInputStream stream = ImageIO.createImageInputStream(gifConn.getInputStream());
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        reader.setInput(stream);

        int frameCount = reader.getNumImages(true);
        int totalWidth = cols * 128;
        int totalHeight = rows * 128;

        BufferedImage[] rawFrames = new BufferedImage[frameCount];
        int[] delays = new int[frameCount];
        BufferedImage canvas = null;
        BufferedImage previousCanvas = null;

        int[] canvasSize = extractCanvasSize(reader);
        int canvasWidth = canvasSize[0];
        int canvasHeight = canvasSize[1];

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

            rawFrames[i] = ImageProcessor.resizeImage(copyImage(canvas), totalWidth, totalHeight);
        }

        reader.dispose();
        stream.close();

        AtomicReferenceArray<byte[]> processedFrames = new AtomicReferenceArray<>(frameCount);

        ForkJoinPool pool = new ForkJoinPool(Math.min(Runtime.getRuntime().availableProcessors(), frameCount));
        pool.submit(() -> {
            java.util.stream.IntStream.range(0, frameCount).parallel().forEach(i -> {
                BufferedImage resized = rawFrames[i];
                BufferedImage[] sections = ImageProcessor.splitIntoMaps(resized, cols, rows);
                byte[] frameBytes = new byte[cols * rows * 128 * 128];
                for (int s = 0; s < sections.length; s++) {
                    byte[] mapBytes = imageToMapColors(sections[s]);
                    System.arraycopy(mapBytes, 0, frameBytes, s * 128 * 128, mapBytes.length);
                }
                processedFrames.set(i, frameBytes);
            });
        }).join();
        pool.shutdown();

        byte[][] framesArray = new byte[frameCount][];
        for (int i = 0; i < frameCount; i++) framesArray[i] = processedFrames.get(i);

        return new GifData(framesArray, delays, cols, rows);
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