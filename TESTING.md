# Руководство по тестированию WebP Converter

## Обзор

Проект содержит комплексный набор тестов для проверки функциональности конвертера изображений.

## Структура тестов

```
src/test/java/com/webpconverter/
├── ImageConverterServiceTest.java       # Unit-тесты (13 тестов)
├── WebPConverterIntegrationTest.java    # Интеграционные тесты (9 тестов)
└── TestImageGenerator.java              # Утилита для генерации тестовых изображений
```

**Всего:** 3 файла, ~655 строк кода, 22 теста

## Запуск тестов

### Все тесты
```bash
mvn test
```

### Только unit-тесты
```bash
mvn test -Dtest=ImageConverterServiceTest
```

### Только интеграционные тесты
```bash
mvn test -Dtest=WebPConverterIntegrationTest
```

### Конкретный тест
```bash
mvn test -Dtest=ImageConverterServiceTest#shouldConvertSingleImageToWebP
```

## Описание тестов

### Unit-тесты (ImageConverterServiceTest)

#### 1. `shouldConvertSingleImageToWebP`
Проверяет базовую конвертацию одиночного изображения.

#### 2. `shouldConvertMultipleImagesWithDifferentFormats`
Тестирует конвертацию нескольких изображений разных форматов (JPG, PNG, BMP).

#### 3. `shouldConvertImagesRecursively`
Проверяет рекурсивную обработку с сохранением структуры папок.

#### 4. `shouldNotProcessSubfoldersWhenRecursiveIsFalse`
Убеждается, что при выключенной рекурсии подпапки не обрабатываются.

#### 5. `shouldIgnoreNonImageFiles`
Проверяет, что не-изображения (txt, xml и т.д.) игнорируются.

#### 6. `shouldReportProgressCorrectly`
Тестирует корректность работы прогресс-коллбэков.

#### 7. `shouldThrowExceptionWhenDirectoryDoesNotExist`
Проверяет обработку ошибки несуществующей директории.

#### 8. `shouldThrowExceptionWhenPathIsNotDirectory`
Проверяет обработку ошибки, когда путь указывает на файл, а не папку.

#### 9. `shouldThrowExceptionWhenNoImagesFound`
Тестирует обработку случая, когда в папке нет изображений.

#### 10. `shouldHandleQualityBoundaries`
Проверяет работу с граничными значениями качества (1 и 100).

#### 11. `shouldPreserveImageDimensions`
Убеждается, что размеры изображения сохраняются после конвертации.

#### 12. `shouldHandleSpecialCharactersInFilenames`
Тестирует обработку имен файлов с кириллицей и специальными символами.

### Интеграционные тесты (WebPConverterIntegrationTest)

#### 1. `shouldConvertCompleteProjectStructure`
Тестирует конвертацию реалистичной структуры проекта с несколькими уровнями вложенности.

#### 2. `shouldHandleMixedContentDirectory`
Проверяет работу с папками, содержащими как изображения, так и другие файлы.

#### 3. `shouldHandleLargeNumberOfFiles`
Тестирует массовую конвертацию (50 файлов) с отслеживанием прогресса.

#### 4. `shouldConvertDifferentQualityLevels`
Проверяет, что разные уровни качества дают разные размеры файлов.

#### 5. `shouldHandleEmptySubdirectories`
Тестирует корректную обработку пустых подпапок.

#### 6. `shouldPreserveImageQualityAndColors`
Проверяет сохранение качества и цветов изображения (с допустимой погрешностью).

#### 7. `shouldHandleAllSupportedFormats`
Тестирует все поддерживаемые форматы: JPG, JPEG, PNG, BMP, GIF.

#### 8. `shouldHandleVeryLargeImage`
Проверяет работу с большими изображениями (2000x1500).

## Вспомогательный класс

### TestImageGenerator

Утилита для создания тестовых изображений:

```java
// Создать изображение
BufferedImage image = TestImageGenerator.createTestImage(
    100, 100,           // размеры
    Color.BLUE,         // цвет фона
    "Test"             // текст
);

// Создать и сохранить изображение
File file = TestImageGenerator.createAndSaveTestImage(
    directory,          // папка
    "test.jpg",        // имя файла
    "jpg",             // формат
    100, 100,          // размеры
    Color.RED,         // цвет
    "Hello"            // текст
);
```

## Проблемы совместимости

### Apple Silicon (M1/M2/M3)

**Проблема:** Библиотека `webp-imageio` версии 0.1.6 не поддерживает ARM64 архитектуру.

**Ошибка:**
```
UnsatisfiedLinkError: ... (mach-o file, but is an incompatible architecture
(have 'x86_64', need 'arm64'))
```

**Временные решения:**

1. **Запуск через Rosetta 2:**
   ```bash
   arch -x86_64 mvn test
   ```

2. **Использование x86_64 Java:**
   ```bash
   # Установить x86_64 версию Java
   /usr/bin/arch -x86_64 /bin/bash
   sdk install java 17.0.x-tem
   mvn test
   ```

3. **Пропуск тестов при сборке:**
   ```bash
   mvn clean package -DskipTests
   ```

**Важно:** Само приложение работает корректно на Apple Silicon. Проблема касается только тестов, требующих нативные библиотеки WebP.

## Покрытие кода

Тесты покрывают:
- ✅ Конвертацию всех поддерживаемых форматов
- ✅ Рекурсивную и нерекурсивную обработку
- ✅ Обработку ошибок и исключений
- ✅ Валидацию входных данных
- ✅ Прогресс-коллбэки
- ✅ Сохранение структуры папок
- ✅ Игнорирование не-изображений
- ✅ Граничные значения параметров
- ✅ Специальные символы в именах файлов
- ✅ Большие и массовые конвертации

## Лучшие практики

### При добавлении новых тестов:

1. **Используйте @TempDir** для временных файлов:
   ```java
   @TempDir
   Path tempDir;
   ```

2. **Очищайте ресурсы** после тестов:
   ```java
   @AfterEach
   void cleanup() {
       // удаление созданных файлов
   }
   ```

3. **Используйте AssertJ** для читаемых assertions:
   ```java
   assertThat(result.getSuccessCount()).isEqualTo(3);
   assertThat(file).exists().isFile();
   ```

4. **Создавайте тестовые данные** через TestImageGenerator:
   ```java
   TestImageGenerator.createAndSaveTestImage(dir, "test.jpg", "jpg", ...);
   ```

## CI/CD интеграция

Для интеграции в CI/CD с x86_64 раннерами:

```yaml
# GitHub Actions пример
- name: Run tests
  run: mvn test

# Для ARM64 раннеров - пропускать тесты
- name: Run tests
  run: mvn test
  continue-on-error: true  # пока не обновится библиотека
```

## Будущие улучшения

- [ ] Обновление на версию webp-imageio с поддержкой ARM64
- [ ] Добавление performance-тестов
- [ ] Увеличение покрытия до 90%+
- [ ] Добавление UI-тестов с TestFX
- [ ] Mock-тесты для изоляции от нативных библиотек
