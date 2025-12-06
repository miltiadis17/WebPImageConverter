package com.webpconverter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TestImageGenerator {

    public static BufferedImage createTestImage(int width, int height, Color backgroundColor, String text) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setColor(backgroundColor);
        graphics.fillRect(0, 0, width, height);

        if (text != null && !text.isEmpty()) {
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = graphics.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            graphics.drawString(text, (width - textWidth) / 2, (height + textHeight) / 2);
        }

        graphics.dispose();
        return image;
    }

    public static File saveImage(BufferedImage image, File directory, String filename, String format) throws IOException {
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File outputFile = new File(directory, filename);
        ImageIO.write(image, format, outputFile);
        return outputFile;
    }

    public static File createAndSaveTestImage(File directory, String filename, String format,
                                               int width, int height, Color color, String text) throws IOException {
        BufferedImage image = createTestImage(width, height, color, text);
        return saveImage(image, directory, filename, format);
    }
}
