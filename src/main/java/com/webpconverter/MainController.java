package com.webpconverter;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class MainController {

    @FXML
    private TextField inputPathField;

    @FXML
    private Button browseButton;

    @FXML
    private Button convertButton;

    @FXML
    private Button cancelButton;

    @FXML
    private CheckBox recursiveCheckBox;

    @FXML
    private Slider qualitySlider;

    @FXML
    private Label qualityLabel;

    @FXML
    private VBox progressBox;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;

    @FXML
    private Label statusLabel;

    private final ImageConverterServiceV2 converterService = new ImageConverterServiceV2();
    private Task<ImageConverterServiceV2.ConversionResult> currentTask;

    @FXML
    public void initialize() {
        qualitySlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            qualityLabel.setText(String.valueOf(newValue.intValue()));
        });

        inputPathField.textProperty().addListener((observable, oldValue, newValue) -> {
            convertButton.setDisable(newValue == null || newValue.trim().isEmpty());
        });
    }

    @FXML
    private void handleBrowse() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Выберите папку с изображениями");

        String currentPath = inputPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }

        File selectedDirectory = directoryChooser.showDialog(browseButton.getScene().getWindow());

        if (selectedDirectory != null) {
            inputPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void handleConvert() {
        String inputPath = inputPathField.getText();
        boolean recursive = recursiveCheckBox.isSelected();
        float quality = (float) qualitySlider.getValue();

        progressBox.setVisible(true);
        progressBox.setManaged(true);
        progressBar.setProgress(0);
        progressLabel.setText("Начало конвертации...");
        statusLabel.setText("");
        statusLabel.setStyle("-fx-background-color: transparent;");

        convertButton.setDisable(true);
        browseButton.setDisable(true);
        cancelButton.setDisable(false);
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);

        currentTask = new Task<>() {
            @Override
            protected ImageConverterServiceV2.ConversionResult call() throws Exception {
                return converterService.convertImagesWithProgress(inputPath, recursive, quality, progressInfo -> {
                    Platform.runLater(() -> {
                        progressBar.setProgress(progressInfo.getProgress());

                        // Форматируем ETA
                        String etaString = formatETA(progressInfo.getEstimatedTimeRemaining());

                        // Форматируем текст прогресса
                        String progressText = String.format(
                            "Обработано: %d/%d (%d%%) • %s • ETA: %s",
                            progressInfo.getProcessedCount(),
                            progressInfo.getTotalFiles(),
                            (int) (progressInfo.getProgress() * 100),
                            progressInfo.getCurrentFile(),
                            etaString
                        );

                        progressLabel.setText(progressText);
                    });
                });
            }
        };

        currentTask.setOnSucceeded(event -> {
            ImageConverterServiceV2.ConversionResult result = currentTask.getValue();

            progressBar.setProgress(1.0);
            progressLabel.setText("Завершено!");

            StringBuilder message = new StringBuilder();
            message.append(String.format("Конвертация завершена!\n\n"));
            message.append(String.format("Всего файлов: %d\n", result.getTotalFiles()));
            message.append(String.format("Успешно: %d\n", result.getSuccessCount()));

            if (result.getFailedCount() > 0) {
                message.append(String.format("Ошибок: %d\n", result.getFailedCount()));
                statusLabel.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
            } else {
                statusLabel.setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724;");
            }

            statusLabel.setText(message.toString());

            if (!result.getErrors().isEmpty()) {
                showErrorDialog(result.getErrors());
            }

            convertButton.setDisable(false);
            browseButton.setDisable(false);
            cancelButton.setDisable(true);
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);
        });

        currentTask.setOnFailed(event -> {
            Throwable exception = currentTask.getException();

            progressBar.setProgress(0);
            progressLabel.setText("");

            String errorMessage = "Ошибка при конвертации:\n" +
                    (exception != null ? exception.getMessage() : "Неизвестная ошибка");

            statusLabel.setText(errorMessage);
            statusLabel.setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24;");

            convertButton.setDisable(false);
            browseButton.setDisable(false);
            cancelButton.setDisable(true);
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);
        });

        currentTask.setOnCancelled(event -> {
            progressBar.setProgress(0);
            progressLabel.setText("Отменено");

            statusLabel.setText("Конвертация была отменена пользователем.");
            statusLabel.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");

            convertButton.setDisable(false);
            browseButton.setDisable(false);
            cancelButton.setDisable(true);
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);
        });

        Thread thread = new Thread(currentTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null && currentTask.isRunning()) {
            converterService.cancel();
            currentTask.cancel();
        }
    }

    private String formatETA(long etaMillis) {
        if (etaMillis <= 0) {
            return "расчет...";
        }

        long seconds = etaMillis / 1000;

        if (seconds < 60) {
            return seconds + " сек";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%d мин %d сек", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%d ч %d мин", hours, minutes);
        }
    }

    private void showErrorDialog(java.util.List<String> errors) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Предупреждение");
        alert.setHeaderText("Некоторые файлы не удалось конвертировать");

        StringBuilder errorText = new StringBuilder();
        for (String error : errors) {
            errorText.append(error).append("\n");
        }

        TextArea textArea = new TextArea(errorText.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        alert.getDialogPane().setExpandableContent(textArea);
        alert.getDialogPane().setExpanded(true);

        alert.showAndWait();
    }
}
