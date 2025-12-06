package com.webpconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class WebPConverterIntegrationTest {

    private ImageConverterService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ImageConverterService();
    }

    @Test
    void shouldConvertCompleteProjectStructure() throws IOException {
        File projectRoot = tempDir.resolve("myproject").toFile();
        projectRoot.mkdirs();

        File imagesDir = new File(projectRoot, "images");
        imagesDir.mkdirs();

        File photosDir = new File(imagesDir, "photos");
        photosDir.mkdirs();

        File iconsDir = new File(imagesDir, "icons");
        iconsDir.mkdirs();

        TestImageGenerator.createAndSaveTestImage(imagesDir, "header.jpg", "jpg",
                800, 200, Color.BLUE, "Header");
        TestImageGenerator.createAndSaveTestImage(photosDir, "photo1.png", "png",
                400, 400, Color.GREEN, "Photo 1");
        TestImageGenerator.createAndSaveTestImage(photosDir, "photo2.jpg", "jpg",
                400, 400, Color.RED, "Photo 2");
        TestImageGenerator.createAndSaveTestImage(iconsDir, "icon.png", "png",
                64, 64, Color.ORANGE, "Icon");

        ImageConverterService.ConversionResult result = service.convertImages(
                imagesDir.getAbsolutePath(),
                true,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(4);
        assertThat(result.getSuccessCount()).isEqualTo(4);
        assertThat(result.getFailedCount()).isEqualTo(0);

        File outputDir = new File(projectRoot, "imagesWebp");
        assertThat(outputDir).exists();
        assertThat(new File(outputDir, "header.webp")).exists();
        assertThat(new File(outputDir, "photos/photo1.webp")).exists();
        assertThat(new File(outputDir, "photos/photo2.webp")).exists();
        assertThat(new File(outputDir, "icons/icon.webp")).exists();
    }

    @Test
    void shouldHandleMixedContentDirectory() throws IOException {
        File inputDir = tempDir.resolve("mixed_content").toFile();
        inputDir.mkdirs();

        TestImageGenerator.createAndSaveTestImage(inputDir, "image1.jpg", "jpg",
                100, 100, Color.CYAN, "1");
        TestImageGenerator.createAndSaveTestImage(inputDir, "image2.png", "png",
                100, 100, Color.MAGENTA, "2");

        Files.writeString(new File(inputDir, "readme.txt").toPath(), "Readme");
        Files.writeString(new File(inputDir, "data.json").toPath(), "{\"key\": \"value\"}");
        new File(inputDir, ".gitignore").createNewFile();

        File subDir = new File(inputDir, "subfolder");
        subDir.mkdirs();
        Files.writeString(new File(subDir, "config.xml").toPath(), "<config/>");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                true,
                80f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
    }

    @Test
    void shouldHandleLargeNumberOfFiles() throws IOException {
        File inputDir = tempDir.resolve("bulk_images").toFile();
        inputDir.mkdirs();

        int fileCount = 50;
        for (int i = 1; i <= fileCount; i++) {
            String format = (i % 2 == 0) ? "jpg" : "png";
            TestImageGenerator.createAndSaveTestImage(inputDir, "image_" + i + "." + format, format,
                    50, 50, new Color(i * 5, i * 3, i * 2), String.valueOf(i));
        }

        List<Double> progressValues = new ArrayList<>();

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                75f,
                progressValues::add
        );

        assertThat(result.getTotalFiles()).isEqualTo(fileCount);
        assertThat(result.getSuccessCount()).isEqualTo(fileCount);
        assertThat(result.getFailedCount()).isEqualTo(0);

        assertThat(progressValues).hasSize(fileCount);
        assertThat(progressValues.get(0)).isCloseTo(1.0 / fileCount, within(0.01));
        assertThat(progressValues.get(progressValues.size() - 1)).isEqualTo(1.0);
    }

    @Test
    void shouldConvertDifferentQualityLevels() throws IOException {
        File inputDir = tempDir.resolve("quality_test").toFile();
        inputDir.mkdirs();

        File originalFile = TestImageGenerator.createAndSaveTestImage(inputDir, "original.jpg", "jpg",
                500, 500, Color.BLUE, "Quality Test");

        long originalSize = originalFile.length();

        float[] qualityLevels = {20f, 50f, 85f, 95f};
        long[] outputSizes = new long[qualityLevels.length];

        for (int i = 0; i < qualityLevels.length; i++) {
            service.convertImages(inputDir.getAbsolutePath(), false, qualityLevels[i], null);

            File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
            File webpFile = new File(outputDir, "original.webp");
            outputSizes[i] = webpFile.length();

            cleanupOutputDirectory(inputDir);
        }

        for (int i = 0; i < outputSizes.length - 1; i++) {
            assertThat(outputSizes[i]).as("Quality %s should be smaller than quality %s",
                    qualityLevels[i], qualityLevels[i + 1]).isLessThan(outputSizes[i + 1]);
        }
    }

    @Test
    void shouldHandleEmptySubdirectories() throws IOException {
        File inputDir = tempDir.resolve("with_empty_dirs").toFile();
        inputDir.mkdirs();

        TestImageGenerator.createAndSaveTestImage(inputDir, "root.jpg", "jpg",
                100, 100, Color.PINK, "Root");

        File emptyDir1 = new File(inputDir, "empty1");
        emptyDir1.mkdirs();

        File subDir = new File(inputDir, "subfolder");
        subDir.mkdirs();
        TestImageGenerator.createAndSaveTestImage(subDir, "sub.png", "png",
                100, 100, Color.YELLOW, "Sub");

        File emptyDir2 = new File(subDir, "empty2");
        emptyDir2.mkdirs();

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                true,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
    }

    @Test
    void shouldPreserveImageQualityAndColors() throws IOException {
        File inputDir = tempDir.resolve("quality_preserve").toFile();
        inputDir.mkdirs();

        Color testColor = new Color(128, 64, 192);
        TestImageGenerator.createAndSaveTestImage(inputDir, "test.png", "png",
                200, 200, testColor, "Color Test");

        service.convertImages(inputDir.getAbsolutePath(), false, 95f, null);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        File webpFile = new File(outputDir, "test.webp");

        BufferedImage convertedImage = ImageIO.read(webpFile);
        assertThat(convertedImage).isNotNull();
        assertThat(convertedImage.getWidth()).isEqualTo(200);
        assertThat(convertedImage.getHeight()).isEqualTo(200);

        Color centerPixel = new Color(convertedImage.getRGB(100, 100));
        assertThat(centerPixel.getRed()).isCloseTo(testColor.getRed(), within(15));
        assertThat(centerPixel.getGreen()).isCloseTo(testColor.getGreen(), within(15));
        assertThat(centerPixel.getBlue()).isCloseTo(testColor.getBlue(), within(15));
    }

    @Test
    void shouldHandleAllSupportedFormats() throws IOException {
        File inputDir = tempDir.resolve("all_formats").toFile();
        inputDir.mkdirs();

        String[] formats = {"jpg", "jpeg", "png", "bmp", "gif"};

        for (String format : formats) {
            TestImageGenerator.createAndSaveTestImage(inputDir, "image." + format, format,
                    100, 100, Color.RED, format.toUpperCase());
        }

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(formats.length);
        assertThat(result.getSuccessCount()).isEqualTo(formats.length);
        assertThat(result.getFailedCount()).isEqualTo(0);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        for (String format : formats) {
            File webpFile = new File(outputDir, "image.webp");
            if (format.equals("jpg")) {
                continue;
            }
            assertThat(new File(outputDir, "image.webp")).exists();
        }
    }

    @Test
    void shouldHandleVeryLargeImage() throws IOException {
        File inputDir = tempDir.resolve("large_image").toFile();
        inputDir.mkdirs();

        TestImageGenerator.createAndSaveTestImage(inputDir, "large.jpg", "jpg",
                2000, 1500, Color.DARK_GRAY, "Large Image");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                80f,
                null
        );

        assertThat(result.getSuccessCount()).isEqualTo(1);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        File webpFile = new File(outputDir, "large.webp");
        assertThat(webpFile).exists();

        BufferedImage image = ImageIO.read(webpFile);
        assertThat(image.getWidth()).isEqualTo(2000);
        assertThat(image.getHeight()).isEqualTo(1500);
    }

    private void cleanupOutputDirectory(File inputDir) throws IOException {
        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        if (outputDir.exists()) {
            deleteDirectory(outputDir);
        }
    }

    private void deleteDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        Files.deleteIfExists(dir.toPath());
    }
}
