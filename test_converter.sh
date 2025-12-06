#!/bin/bash

# Тестовый скрипт для проверки конвертера

echo "=== WebP Converter Test ==="
echo ""

INPUT_DIR="/Users/miltiadiskurdzidis/PetProjects/pomegranate/public/video_frames-0"

if [ ! -d "$INPUT_DIR" ]; then
    echo "Ошибка: Папка $INPUT_DIR не существует"
    exit 1
fi

echo "Входная папка: $INPUT_DIR"
echo "Количество PNG файлов: $(ls $INPUT_DIR/*.png 2>/dev/null | wc -l)"
echo ""

# Создаем простой Java класс для теста
cat > /tmp/TestConversion.java << 'EOF'
import com.webpconverter.ImageConverterService;
import com.webpconverter.ImageConverterService.ConversionResult;

public class TestConversion {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Использование: java TestConversion <путь_к_папке>");
            System.exit(1);
        }

        String inputPath = args[0];
        System.out.println("Начало конвертации: " + inputPath);

        ImageConverterService service = new ImageConverterService();

        try {
            ConversionResult result = service.convertImages(
                inputPath,
                false,  // не рекурсивно
                85f,    // качество 85
                progress -> {
                    int percentage = (int)(progress * 100);
                    if (percentage % 10 == 0 || percentage == 100) {
                        System.out.println("Прогресс: " + percentage + "%");
                    }
                }
            );

            System.out.println("\n=== Результат ===");
            System.out.println("Всего файлов: " + result.getTotalFiles());
            System.out.println("Успешно: " + result.getSuccessCount());
            System.out.println("Ошибок: " + result.getFailedCount());

            if (!result.getErrors().isEmpty()) {
                System.out.println("\nОшибки:");
                for (String error : result.getErrors()) {
                    System.out.println("  - " + error);
                }
            }

        } catch (Exception e) {
            System.err.println("Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOF

echo "Компиляция тестового класса..."
javac -cp target/classes /tmp/TestConversion.java

if [ $? -ne 0 ]; then
    echo "Ошибка компиляции тестового класса"
    exit 1
fi

echo ""
echo "Запуск конвертации..."
echo "========================================"
java -cp target/classes:/tmp com.webpconverter.TestConversion "$INPUT_DIR"

echo ""
echo "========================================"
echo ""

OUTPUT_DIR="${INPUT_DIR}Webp"
if [ -d "$OUTPUT_DIR" ]; then
    WEBP_COUNT=$(ls $OUTPUT_DIR/*.webp 2>/dev/null | wc -l)
    echo "Выходная папка: $OUTPUT_DIR"
    echo "Создано WebP файлов: $WEBP_COUNT"

    if [ $WEBP_COUNT -gt 0 ]; then
        echo ""
        echo "Примеры созданных файлов:"
        ls -lh $OUTPUT_DIR/*.webp | head -5
    fi
else
    echo "Выходная папка не была создана"
fi

# Очистка
rm /tmp/TestConversion.java /tmp/TestConversion.class 2>/dev/null

echo ""
echo "Тест завершен"
