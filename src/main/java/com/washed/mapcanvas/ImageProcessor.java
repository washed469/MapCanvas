package com.washed.mapcanvas;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

public class ImageProcessor {

    public static BufferedImage downloadImage(String url) throws IOException {
        URL imageUrl = URI.create(url).toURL();
        BufferedImage img = ImageIO.read(imageUrl);
        if (img == null) throw new IOException("Could not read image from URL");
        return img;
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