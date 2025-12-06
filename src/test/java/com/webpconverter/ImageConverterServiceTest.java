package com.webpconverter;

import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class ImageConverterServiceTest {

    private ImageConverterService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ImageConverterService();
    }

    @Test
    void shouldConvertSingleImageToWebP() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "test1.jpg", "jpg");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(outputDir).exists().isDirectory();
        assertThat(new File(outputDir, "test1.webp")).exists();
    }

    @Test
    void shouldConvertMultipleImagesWithDifferentFormats() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "image1.jpg", "jpg");
        createTestImage(inputDir, "image2.png", "png");
        createTestImage(inputDir, "image3.bmp", "bmp");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                90f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(result.getFailedCount()).isEqualTo(0);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(new File(outputDir, "image1.webp")).exists();
        assertThat(new File(outputDir, "image2.webp")).exists();
        assertThat(new File(outputDir, "image3.webp")).exists();
    }

    @Test
    void shouldConvertImagesRecursively() throws IOException {
        File inputDir = createTestImageDirectory(true);
        createTestImage(inputDir, "root.jpg", "jpg");

        File subDir1 = new File(inputDir, "subfolder1");
        subDir1.mkdirs();
        createTestImage(subDir1, "sub1.png", "png");

        File subDir2 = new File(subDir1, "subfolder2");
        subDir2.mkdirs();
        createTestImage(subDir2, "sub2.jpg", "jpg");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                true,
                80f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(3);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(new File(outputDir, "root.webp")).exists();
        assertThat(new File(outputDir, "subfolder1/sub1.webp")).exists();
        assertThat(new File(outputDir, "subfolder1/subfolder2/sub2.webp")).exists();
    }

    @Test
    void shouldNotProcessSubfoldersWhenRecursiveIsFalse() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "root.jpg", "jpg");

        File subDir = new File(inputDir, "subfolder");
        subDir.mkdirs();
        createTestImage(subDir, "sub.jpg", "jpg");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(new File(outputDir, "root.webp")).exists();
        assertThat(new File(outputDir, "subfolder/sub.webp")).doesNotExist();
    }

    @Test
    void shouldIgnoreNonImageFiles() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "image.jpg", "jpg");

        File textFile = new File(inputDir, "readme.txt");
        Files.writeString(textFile.toPath(), "This is a text file");

        File xmlFile = new File(inputDir, "config.xml");
        Files.writeString(xmlFile.toPath(), "<config></config>");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void shouldReportProgressCorrectly() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "img1.jpg", "jpg");
        createTestImage(inputDir, "img2.jpg", "jpg");
        createTestImage(inputDir, "img3.jpg", "jpg");

        AtomicInteger progressCallCount = new AtomicInteger(0);
        AtomicReference<Double> lastProgress = new AtomicReference<>(0.0);

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                progress -> {
                    progressCallCount.incrementAndGet();
                    lastProgress.set(progress);
                }
        );

        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(progressCallCount.get()).isEqualTo(3);
        assertThat(lastProgress.get()).isEqualTo(1.0);
    }

    @Test
    void shouldThrowExceptionWhenDirectoryDoesNotExist() {
        assertThatThrownBy(() ->
                service.convertImages("/nonexistent/path", false, 85f, null)
        )
                .isInstanceOf(IOException.class)
                .hasMessageContaining("не существует");
    }

    @Test
    void shouldThrowExceptionWhenPathIsNotDirectory() throws IOException {
        File file = new File(tempDir.toFile(), "not_a_directory.txt");
        Files.writeString(file.toPath(), "content");

        assertThatThrownBy(() ->
                service.convertImages(file.getAbsolutePath(), false, 85f, null)
        )
                .isInstanceOf(IOException.class)
                .hasMessageContaining("не является директорией");
    }

    @Test
    void shouldThrowExceptionWhenNoImagesFound() throws IOException {
        File inputDir = createTestImageDirectory(false);
        Files.writeString(new File(inputDir, "readme.txt").toPath(), "No images here");

        assertThatThrownBy(() ->
                service.convertImages(inputDir.getAbsolutePath(), false, 85f, null)
        )
                .isInstanceOf(IOException.class)
                .hasMessageContaining("не найдено изображений");
    }

    @Test
    void shouldHandleQualityBoundaries() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "test.jpg", "jpg");

        ImageConverterService.ConversionResult result1 = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                1f,
                null
        );
        assertThat(result1.getSuccessCount()).isEqualTo(1);

        cleanupOutputDirectory(inputDir);

        ImageConverterService.ConversionResult result2 = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                100f,
                null
        );
        assertThat(result2.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void shouldPreserveImageDimensions() throws IOException {
        File inputDir = createTestImageDirectory(false);
        File inputFile = createTestImage(inputDir, "test.jpg", "jpg");

        BufferedImage originalImage = ImageIO.read(inputFile);
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        service.convertImages(inputDir.getAbsolutePath(), false, 85f, null);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        File outputFile = new File(outputDir, "test.webp");

        BufferedImage convertedImage = ImageIO.read(outputFile);
        assertThat(convertedImage.getWidth()).isEqualTo(originalWidth);
        assertThat(convertedImage.getHeight()).isEqualTo(originalHeight);
    }

    @Test
    void shouldHandleSpecialCharactersInFilenames() throws IOException {
        File inputDir = createTestImageDirectory(false);
        createTestImage(inputDir, "тест изображение 123.jpg", "jpg");
        createTestImage(inputDir, "image-with-dashes.png", "png");

        ImageConverterService.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(new File(outputDir, "тест изображение 123.webp")).exists();
        assertThat(new File(outputDir, "image-with-dashes.webp")).exists();
    }

    private File createTestImageDirectory(boolean withSubfolders) throws IOException {
        String dirName = withSubfolders ? "test_images_recursive" : "test_images";
        File dir = new File(tempDir.toFile(), dirName);
        dir.mkdirs();
        return dir;
    }

    private File createTestImage(File directory, String filename, String format) throws IOException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 100, 100);
        graphics.setColor(Color.WHITE);
        graphics.drawString("Test", 40, 50);
        graphics.dispose();

        File outputFile = new File(directory, filename);
        ImageIO.write(image, format, outputFile);
        return outputFile;
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
