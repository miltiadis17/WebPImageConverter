package com.webpconverter;

import com.sksamuel.scrimage.ImmutableImage;
import java.io.File;

public class DiagnosticTool {
    public static void main(String[] args) {
        String inputPath = "/Users/miltiadiskurdzidis/PetProjects/pomegranate/public/video_frames-0";

        System.out.println("=== WebP Converter Diagnostic Tool V2 ===\n");
        System.out.println("Используется библиотека Scrimage (с поддержкой ARM64)\n");

        File inputDir = new File(inputPath);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            System.err.println("Папка не существует: " + inputPath);
            return;
        }

        System.out.println("Входная папка: " + inputPath);
        File[] files = inputDir.listFiles();

        if (files == null) {
            System.err.println("Не удалось прочитать содержимое папки");
            return;
        }

        int pngCount = 0;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                pngCount++;
            }
        }

        System.out.println("Найдено PNG файлов: " + pngCount);

        System.out.println("\nЗапуск тестовой конвертации...\n");

        ImageConverterServiceV2 service = new ImageConverterServiceV2();

        try {
            ImageConverterServiceV2.ConversionResult result = service.convertImages(
                inputPath,
                false,
                85f,
                progress -> {
                    int percentage = (int)(progress * 100);
                    System.out.print("\rПрогресс: " + percentage + "%");
                    System.out.flush();
                }
            );

            System.out.println("\n\n=== РЕЗУЛЬТАТ ===");
            System.out.println("Всего файлов обнаружено: " + result.getTotalFiles());
            System.out.println("Успешно конвертировано: " + result.getSuccessCount());
            System.out.println("Ошибок: " + result.getFailedCount());

            if (result.getFailedCount() > 0) {
                System.out.println("\n=== ОШИБКИ ===");
                for (String error : result.getErrors()) {
                    System.out.println("  • " + error);
                }
            }

            String outputPath = inputPath + "Webp";
            File outputDir = new File(outputPath);
            if (outputDir.exists()) {
                File[] outputFiles = outputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".webp"));
                System.out.println("\n=== ВЫХОДНАЯ ПАПКА ===");
                System.out.println("Путь: " + outputPath);
                System.out.println("Создано WebP файлов: " + (outputFiles != null ? outputFiles.length : 0));

                if (outputFiles != null && outputFiles.length > 0) {
                    System.out.println("\nПримеры созданных файлов:");
                    for (int i = 0; i < Math.min(5, outputFiles.length); i++) {
                        File f = outputFiles[i];
                        System.out.printf("  %s (%.2f KB)\n", f.getName(), f.length() / 1024.0);

                        // Проверим, можно ли прочитать webp
                        try {
                            ImmutableImage img = ImmutableImage.loader().fromFile(f);
                            if (img != null) {
                                System.out.printf("    ✓ Читается корректно: %dx%d\n", img.width, img.height);
                            } else {
                                System.out.println("    ✗ Не удалось прочитать изображение");
                            }
                        } catch (Exception e) {
                            System.out.println("    ✗ Ошибка чтения: " + e.getMessage());
                        }
                    }
                }
            } else {
                System.out.println("\n✗ Выходная папка не была создана");
            }

        } catch (Exception e) {
            System.err.println("\n✗ КРИТИЧЕСКАЯ ОШИБКА:");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
