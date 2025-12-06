package com.webpconverter;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ImageConverterServiceV2 {

    private static final String[] SUPPORTED_FORMATS = {"jpg", "jpeg", "png", "bmp", "gif"};
    private static final String OUTPUT_SUFFIX = "Webp";

    private ExecutorService executorService;
    private volatile boolean cancelled = false;

    public static class ConversionResult {
        private final int totalFiles;
        private final int successCount;
        private final int failedCount;
        private final List<String> errors;

        public ConversionResult(int totalFiles, int successCount, int failedCount, List<String> errors) {
            this.totalFiles = totalFiles;
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.errors = errors;
        }

        public int getTotalFiles() { return totalFiles; }
        public int getSuccessCount() { return successCount; }
        public int getFailedCount() { return failedCount; }
        public List<String> getErrors() { return errors; }
    }

    public static class ProgressInfo {
        private final double progress;
        private final String currentFile;
        private final int processedCount;
        private final int totalFiles;
        private final long estimatedTimeRemaining; // в миллисекундах

        public ProgressInfo(double progress, String currentFile, int processedCount, int totalFiles, long estimatedTimeRemaining) {
            this.progress = progress;
            this.currentFile = currentFile;
            this.processedCount = processedCount;
            this.totalFiles = totalFiles;
            this.estimatedTimeRemaining = estimatedTimeRemaining;
        }

        public double getProgress() { return progress; }
        public String getCurrentFile() { return currentFile; }
        public int getProcessedCount() { return processedCount; }
        public int getTotalFiles() { return totalFiles; }
        public long getEstimatedTimeRemaining() { return estimatedTimeRemaining; }
    }

    /**
     * Конвертирует изображения с простым коллбэком прогресса
     */
    public ConversionResult convertImages(String inputPath, boolean recursive, float quality,
                                          Consumer<Double> progressCallback) throws IOException {
        // Оборачиваем простой коллбэк в расширенный для обратной совместимости
        return convertImagesWithProgress(inputPath, recursive, quality,
            progressCallback == null ? null : progressInfo -> progressCallback.accept(progressInfo.getProgress()));
    }

    /**
     * Конвертирует изображения с расширенным коллбэком прогресса (файл, ETA и т.д.)
     */
    public ConversionResult convertImagesWithProgress(String inputPath, boolean recursive, float quality,
                                                      Consumer<ProgressInfo> progressCallback) throws IOException {
        File inputDir = new File(inputPath);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IOException("Указанная папка не существует или не является директорией");
        }

        List<File> imageFiles = findImageFiles(inputDir, recursive);
        if (imageFiles.isEmpty()) {
            throw new IOException("В указанной папке не найдено изображений");
        }

        String outputPath = createOutputDirectory(inputPath);

        // Сбрасываем флаг отмены перед началом
        cancelled = false;

        // Определяем количество потоков (CPU cores)
        int threadCount = Runtime.getRuntime().availableProcessors();
        executorService = Executors.newFixedThreadPool(threadCount);

        // Атомарные счетчики для потокобезопасности
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        // Время начала конвертации для расчета ETA
        long startTime = System.currentTimeMillis();

        List<Future<?>> futures = new ArrayList<>();

        try {
            // Отправляем задачи в пул потоков
            for (File imageFile : imageFiles) {
                // Проверяем флаг отмены перед добавлением новых задач
                if (cancelled) {
                    break;
                }

                Future<?> future = executorService.submit(() -> {
                    // Проверяем флаг отмены в начале задачи
                    if (cancelled) {
                        int processed = processedCount.incrementAndGet();
                        if (progressCallback != null) {
                            long eta = calculateETA(startTime, processed, imageFiles.size());
                            ProgressInfo progressInfo = new ProgressInfo(
                                processed / (double) imageFiles.size(),
                                "",
                                processed,
                                imageFiles.size(),
                                eta
                            );
                            progressCallback.accept(progressInfo);
                        }
                        return;
                    }

                    try {
                        String relativePath = getRelativePath(inputDir, imageFile);
                        convertToWebP(imageFile, outputPath, relativePath, quality);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        String errorMsg = imageFile.getName() + ": " + e.getMessage();
                        if (e.getCause() != null) {
                            errorMsg += " (Причина: " + e.getCause().getMessage() + ")";
                        }
                        errors.add(errorMsg);
                        System.err.println("Ошибка конвертации " + imageFile.getAbsolutePath() + ": " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        // Обновляем прогресс после каждого файла
                        int processed = processedCount.incrementAndGet();
                        if (progressCallback != null) {
                            long eta = calculateETA(startTime, processed, imageFiles.size());
                            ProgressInfo progressInfo = new ProgressInfo(
                                processed / (double) imageFiles.size(),
                                imageFile.getName(),
                                processed,
                                imageFiles.size(),
                                eta
                            );
                            progressCallback.accept(progressInfo);
                        }
                    }
                });
                futures.add(future);
            }

            // Ожидаем завершения всех задач
            for (Future<?> future : futures) {
                try {
                    future.get(); // Блокируемся до завершения задачи
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Конвертация была прервана", e);
                } catch (ExecutionException e) {
                    // Ошибки уже обработаны внутри задач
                    System.err.println("Ошибка выполнения задачи: " + e.getMessage());
                }
            }

        } finally {
            // Корректно завершаем ExecutorService
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return new ConversionResult(imageFiles.size(), successCount.get(), failedCount.get(), new ArrayList<>(errors));
    }

    /**
     * Отменяет текущую конвертацию
     */
    public void cancel() {
        cancelled = true;
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    /**
     * Проверяет, была ли конвертация отменена
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Рассчитывает расчетное время до завершения (ETA) в миллисекундах
     */
    private long calculateETA(long startTime, int processedFiles, int totalFiles) {
        if (processedFiles == 0) {
            return 0;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        double averageTimePerFile = (double) elapsedTime / processedFiles;
        int remainingFiles = totalFiles - processedFiles;

        return (long) (averageTimePerFile * remainingFiles);
    }

    private List<File> findImageFiles(File directory, boolean recursive) throws IOException {
        List<File> imageFiles = new ArrayList<>();

        if (recursive) {
            try (Stream<Path> paths = Files.walk(directory.toPath())) {
                paths.filter(Files::isRegularFile)
                     .filter(this::isImageFile)
                     .forEach(path -> imageFiles.add(path.toFile()));
            }
        } else {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && isImageFile(file.toPath())) {
                        imageFiles.add(file);
                    }
                }
            }
        }

        return imageFiles;
    }

    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        for (String format : SUPPORTED_FORMATS) {
            if (fileName.endsWith("." + format)) {
                return true;
            }
        }
        return false;
    }

    private String createOutputDirectory(String inputPath) throws IOException {
        File inputDir = new File(inputPath);
        String parentPath = inputDir.getParent();
        String dirName = inputDir.getName();

        String outputPath = parentPath != null
            ? Paths.get(parentPath, dirName + OUTPUT_SUFFIX).toString()
            : dirName + OUTPUT_SUFFIX;

        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            Files.createDirectories(outputDir.toPath());
        }

        return outputPath;
    }

    private String getRelativePath(File baseDir, File file) {
        Path basePath = baseDir.toPath();
        Path filePath = file.toPath();
        Path relativePath = basePath.relativize(filePath);
        return relativePath.toString();
    }

    private void convertToWebP(File inputFile, String outputBasePath, String relativePath, float quality)
            throws IOException {
        // Загружаем изображение с помощью Scrimage
        ImmutableImage image = ImmutableImage.loader().fromFile(inputFile);

        String outputFileName = changeExtension(relativePath, "webp");
        File outputFile = new File(outputBasePath, outputFileName);

        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            Files.createDirectories(outputDir.toPath());
        }

        // Создаем WebP writer с заданным качеством
        WebpWriter writer = WebpWriter.DEFAULT.withQ((int) quality);

        // Сохраняем изображение в WebP формате
        image.output(writer, outputFile);
    }

    private String changeExtension(String fileName, String newExtension) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot) + "." + newExtension;
        }
        return fileName + "." + newExtension;
    }
}
