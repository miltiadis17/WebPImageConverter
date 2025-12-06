package com.webpconverter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.PngWriter;

import static org.assertj.core.api.Assertions.*;

class ImageConverterServiceV2Test {

    private ImageConverterServiceV2 service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ImageConverterServiceV2();
    }

    @Test
    void shouldConvertMultipleImagesInParallel() throws IOException {
        File inputDir = createTestImageDirectory("test_parallel");

        // Создаем несколько тестовых изображений
        for (int i = 1; i <= 10; i++) {
            createTestImage(inputDir, "test" + i + ".png", "png");
        }

        long startTime = System.currentTimeMillis();
        ImageConverterServiceV2.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                null
        );
        long endTime = System.currentTimeMillis();

        assertThat(result.getTotalFiles()).isEqualTo(10);
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailedCount()).isEqualTo(0);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(outputDir).exists().isDirectory();

        // Проверяем, что все файлы сконвертированы
        for (int i = 1; i <= 10; i++) {
            assertThat(new File(outputDir, "test" + i + ".webp")).exists();
        }

        System.out.println("Conversion time: " + (endTime - startTime) + "ms");
    }

    @Test
    void shouldCancelConversion() throws IOException, InterruptedException {
        File inputDir = createTestImageDirectory("test_cancel");

        // Создаем много файлов для длительной конвертации
        for (int i = 1; i <= 100; i++) {
            createTestImage(inputDir, "test" + i + ".png", "png");
        }

        AtomicInteger progressCount = new AtomicInteger(0);
        CountDownLatch startedLatch = new CountDownLatch(1);

        // Запускаем конвертацию в отдельном потоке
        Thread conversionThread = new Thread(() -> {
            try {
                service.convertImages(
                        inputDir.getAbsolutePath(),
                        false,
                        85f,
                        progress -> {
                            progressCount.incrementAndGet();
                            startedLatch.countDown();
                        }
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        conversionThread.start();

        // Ждем, пока конвертация начнется
        boolean started = startedLatch.await(5, TimeUnit.SECONDS);
        assertThat(started).isTrue();

        // Немного подождем, чтобы обработалось несколько файлов
        Thread.sleep(100);

        // Отменяем конвертацию
        service.cancel();

        // Ждем завершения потока
        conversionThread.join(5000);

        // Проверяем, что конвертация была отменена
        assertThat(service.isCancelled()).isTrue();

        // Проверяем, что обработано меньше 100 файлов
        int processed = progressCount.get();
        System.out.println("Processed " + processed + " files before cancellation");
        assertThat(processed).isLessThan(100);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        if (outputDir.exists()) {
            File[] outputFiles = outputDir.listFiles((dir, name) -> name.endsWith(".webp"));
            if (outputFiles != null) {
                System.out.println("Output files created: " + outputFiles.length);
                // Должно быть создано меньше файлов, чем всего
                assertThat(outputFiles.length).isLessThanOrEqualTo(processed);
            }
        }
    }

    @Test
    void shouldHandleProgressCallback() throws IOException {
        File inputDir = createTestImageDirectory("test_progress");

        for (int i = 1; i <= 5; i++) {
            createTestImage(inputDir, "test" + i + ".jpg", "jpg");
        }

        AtomicInteger progressUpdates = new AtomicInteger(0);

        ImageConverterServiceV2.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                false,
                85f,
                progress -> {
                    progressUpdates.incrementAndGet();
                    assertThat(progress).isBetween(0.0, 1.0);
                }
        );

        assertThat(result.getSuccessCount()).isEqualTo(5);
        // Должно быть минимум 5 обновлений прогресса (по одному на файл)
        assertThat(progressUpdates.get()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void shouldProvideDetailedProgressInfo() throws IOException {
        File inputDir = createTestImageDirectory("test_detailed_progress");

        for (int i = 1; i <= 10; i++) {
            createTestImage(inputDir, "test" + i + ".jpg", "jpg");
        }

        AtomicInteger progressUpdates = new AtomicInteger(0);
        StringBuilder progressLog = new StringBuilder();

        ImageConverterServiceV2.ConversionResult result = service.convertImagesWithProgress(
                inputDir.getAbsolutePath(),
                false,
                85f,
                progressInfo -> {
                    progressUpdates.incrementAndGet();

                    // Проверяем, что все поля заполнены
                    assertThat(progressInfo.getProgress()).isBetween(0.0, 1.0);
                    assertThat(progressInfo.getProcessedCount()).isGreaterThan(0);
                    assertThat(progressInfo.getTotalFiles()).isEqualTo(10);
                    assertThat(progressInfo.getEstimatedTimeRemaining()).isGreaterThanOrEqualTo(0L);

                    // Логируем прогресс для отладки
                    progressLog.append(String.format(
                        "Progress: %.1f%%, File: %s, Processed: %d/%d, ETA: %dms\n",
                        progressInfo.getProgress() * 100,
                        progressInfo.getCurrentFile(),
                        progressInfo.getProcessedCount(),
                        progressInfo.getTotalFiles(),
                        progressInfo.getEstimatedTimeRemaining()
                    ));
                }
        );

        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(progressUpdates.get()).isGreaterThanOrEqualTo(10);

        System.out.println("=== DETAILED PROGRESS LOG ===");
        System.out.println(progressLog.toString());
    }

    @Test
    void shouldConvertRecursively() throws IOException {
        File inputDir = createTestImageDirectory("test_recursive");

        // Создаем изображения в корневой папке
        createTestImage(inputDir, "root1.png", "png");
        createTestImage(inputDir, "root2.jpg", "jpg");

        // Создаем подпапку с изображениями
        File subDir = new File(inputDir, "subfolder");
        subDir.mkdirs();
        createTestImage(subDir, "sub1.png", "png");
        createTestImage(subDir, "sub2.bmp", "bmp");

        ImageConverterServiceV2.ConversionResult result = service.convertImages(
                inputDir.getAbsolutePath(),
                true,  // recursive
                85f,
                null
        );

        assertThat(result.getTotalFiles()).isEqualTo(4);
        assertThat(result.getSuccessCount()).isEqualTo(4);
        assertThat(result.getFailedCount()).isEqualTo(0);

        File outputDir = new File(inputDir.getParent(), inputDir.getName() + "Webp");
        assertThat(outputDir).exists().isDirectory();

        assertThat(new File(outputDir, "root1.webp")).exists();
        assertThat(new File(outputDir, "root2.webp")).exists();

        File outputSubDir = new File(outputDir, "subfolder");
        assertThat(outputSubDir).exists().isDirectory();
        assertThat(new File(outputSubDir, "sub1.webp")).exists();
        assertThat(new File(outputSubDir, "sub2.webp")).exists();
    }

    private File createTestImageDirectory(String name) throws IOException {
        Path dirPath = tempDir.resolve(name);
        Files.createDirectories(dirPath);
        return dirPath.toFile();
    }

    private void createTestImage(File directory, String fileName, String format) throws IOException {
        BufferedImage bufferedImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bufferedImage.createGraphics();

        // Рисуем простое изображение с цветным фоном
        g2d.setColor(new Color((int)(Math.random() * 255), (int)(Math.random() * 255), (int)(Math.random() * 255)));
        g2d.fillRect(0, 0, 100, 100);
        g2d.dispose();

        // Сохраняем с помощью Scrimage для совместимости
        ImmutableImage image = ImmutableImage.fromAwt(bufferedImage);
        File outputFile = new File(directory, fileName);
        image.output(PngWriter.MinCompression, outputFile);
    }
}
