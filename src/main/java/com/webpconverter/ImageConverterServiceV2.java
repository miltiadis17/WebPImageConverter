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
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ImageConverterServiceV2 {

    private static final String[] SUPPORTED_FORMATS = {"jpg", "jpeg", "png", "bmp", "gif"};
    private static final String OUTPUT_SUFFIX = "Webp";

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

    public ConversionResult convertImages(String inputPath, boolean recursive, float quality,
                                          Consumer<Double> progressCallback) throws IOException {
        File inputDir = new File(inputPath);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IOException("Указанная папка не существует или не является директорией");
        }

        List<File> imageFiles = findImageFiles(inputDir, recursive);
        if (imageFiles.isEmpty()) {
            throw new IOException("В указанной папке не найдено изображений");
        }

        String outputPath = createOutputDirectory(inputPath);

        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < imageFiles.size(); i++) {
            File imageFile = imageFiles.get(i);
            try {
                String relativePath = getRelativePath(inputDir, imageFile);
                convertToWebP(imageFile, outputPath, relativePath, quality);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                String errorMsg = imageFile.getName() + ": " + e.getMessage();
                if (e.getCause() != null) {
                    errorMsg += " (Причина: " + e.getCause().getMessage() + ")";
                }
                errors.add(errorMsg);
                System.err.println("Ошибка конвертации " + imageFile.getAbsolutePath() + ": " + e.getMessage());
                e.printStackTrace();
            }

            if (progressCallback != null) {
                double progress = (i + 1) / (double) imageFiles.size();
                progressCallback.accept(progress);
            }
        }

        return new ConversionResult(imageFiles.size(), successCount, failedCount, errors);
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
