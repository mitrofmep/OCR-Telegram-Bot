package com.example.springbot_reborn.service;

import com.example.springbot_reborn.config.BotConfig;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    public TelegramBot(BotConfig config) {
        this.config = config;
    }

    @Override
    public String getBotUsername() {
        return config.getBot_name();
    }

    @Override
    public String getBotToken() {
        return config.getBot_token();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            sendMessage(update.getMessage().getChatId(), "Hello!");
        }

        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            PhotoSize photo = getPhoto(update);
            String photoPath = getFilePath(photo);
            java.io.File resultFile = downloadPhotoByFilePath(photoPath);
            tesseractPhoto(resultFile, update);
        }
    }

    public void sendMessage(long chat_id, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chat_id));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public PhotoSize getPhoto(Update update) {
        List<PhotoSize> photos = update.getMessage().getPhoto();
        return photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize)).orElse(null);
    }

    public String getFilePath(PhotoSize photo) {
        Objects.requireNonNull(photo);
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(photo.getFileId());
        try {
            File file = execute(getFileMethod);
            return file.getFilePath();
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        return null;
    }

    public java.io.File downloadPhotoByFilePath(String filePath) {
        try {
            return downloadFile(filePath, new java.io.File("src/main/resources/photos/photo.jpg"));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void tesseractPhoto(java.io.File resultFile, Update update) {
        Tesseract instance = new Tesseract();
        instance.setDatapath("src/main/resources/tessdata");
        try {
            String result = instance.doOCR(resultFile);
            sendMessage(update.getMessage().getChatId(), result);
        } catch (TesseractException e) {

        }
    }
}
