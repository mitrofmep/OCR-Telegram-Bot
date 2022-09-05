package com.example.springbot_reborn.service;

import com.example.springbot_reborn.config.BotConfig;
//import net.sourceforge.tess4j.Tesseract;
//import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class TelegramBot extends TelegramLongPollingBot {
    private HashMap<String, String> users = new HashMap<>();

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
            Message message = update.getMessage();
            String text = message.getText();
            if (text.equals("/start")) {
                sendInlineKeyboard(update);
            } else sendMessage(update.getMessage().getChatId(), "Извините, команда не распознана. " +
                    "Для начала работы отправьте /start");

        } else if (update.hasCallbackQuery()) {
            String lang = update.getCallbackQuery().getData();
            System.out.println("lang is " + lang);
            System.out.println("keys are " + users.keySet().toString() + " and values are " + users.values().toString());
            users.put(update.getCallbackQuery().getFrom().getId().toString(), lang);
            System.out.println("keys are " + users.keySet().toString() + " and values are " + users.values().toString());
            sendMessage(update.getCallbackQuery().getFrom().getId(), "Отлично! Теперь отправьте мне изображение");
        }

        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            if (!users.containsKey(update.getMessage().getChatId().toString())) {
                sendMessage(update.getMessage().getChatId(), "Сначала отправьте мне команду /start");
            } else {
                SendChatAction sendChatAction = new SendChatAction();
                sendChatAction.setAction(ActionType.TYPING);
                sendChatAction.setChatId(update.getMessage().getChatId());
                try {
                    Boolean wasSuccessfull = execute(sendChatAction);
                    photoReceived(update);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void photoReceived(Update update) {

        PhotoSize photo = getPhoto(update);
        String photoPath = getFilePath(photo);
        Random rand = new Random();
        String fileNameSuffix = String.valueOf(rand.nextInt(1000));
        java.io.File resultFile = downloadPhotoByFilePath(photoPath, fileNameSuffix);
        System.out.println("abs path is " + resultFile.getAbsolutePath());
        System.out.println("path is " + resultFile.getPath());
        try {
            tesseractPhotoCMD(resultFile, update);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendInlineKeyboard(Update update) {
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText("Выберите язык текста на вашем изображении");

        // Create InlineKeyboardMarkup object
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        // Create the keyboard (list of InlineKeyboardButton list)
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        // Create a list for buttons
        List<InlineKeyboardButton> Buttons = new ArrayList<InlineKeyboardButton>();
        // Initialize each button, the text must be written
        InlineKeyboardButton en= new InlineKeyboardButton("EN");
        // Also must use exactly one of the optional fields,it can edit  by set method
        en.setCallbackData("eng");
        // Add button to the list
        Buttons.add(en);
        // Initialize each button, the text must be written
        InlineKeyboardButton ru= new InlineKeyboardButton("RU");
        // Also must use exactly one of the optional fields,it can edit  by set method
        ru.setCallbackData("rus");
        // Add button to the list
        Buttons.add(ru);
        keyboard.add(Buttons);
        inlineKeyboardMarkup.setKeyboard(keyboard);
        // Add it to the message
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            // Send the message
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
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

    public java.io.File downloadPhotoByFilePath(String filePath, String fileNameSuffix) {
        try {
            java.io.File file1 = new java.io.File("/app/src/main/resources/photos", "photo.jpg");
            file1.createNewFile();
            return downloadFile(filePath, file1);
        } catch (TelegramApiException e) {

            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    public void tesseractPhotoCMD(java.io.File resultFile, Update update) throws IOException {
        Process process = new ProcessBuilder("tesseract", "--tessdata-dir /app/src/main/resources/tessdata",
                resultFile.getPath(),
                "src/main/resources/output", "-l",
                users.get(update.getMessage().getChatId().toString())).start();
        System.out.println("tesseracting here to " + resultFile.getPath());
        try {
            process.waitFor();
            whenReadWithBufferedReader_thenCorrect(update);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void whenReadWithBufferedReader_thenCorrect(Update update)
            throws IOException {
        String file = "src/main/resources/output.txt";
        System.out.println("file here is " + file);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(" ");
        }
        sendMessage(update.getMessage().getChatId(), builder.toString());
        reader.close();
        sendInlineKeyboard(update);
    }
}
