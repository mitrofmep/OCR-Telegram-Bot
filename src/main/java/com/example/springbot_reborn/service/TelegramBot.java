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
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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
            users.put(update.getCallbackQuery().getFrom().getId().toString(), lang);
            sendMessage(update.getCallbackQuery().getFrom().getId(), "Отлично! Теперь отправьте мне изображение");
        }

        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            if (!users.containsKey(update.getMessage().getChatId().toString())) {
                sendMessage(update.getMessage().getChatId(), "Сначала отправьте мне команду /start и выберите язык");
            } else {
                SendChatAction sendChatAction = new SendChatAction();
                sendChatAction.setAction(ActionType.TYPING);
                sendChatAction.setChatId(update.getMessage().getChatId());
                try {
                    execute(sendChatAction);
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
        String fileNameSuffix = String.valueOf(rand.nextInt(10000));
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
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> Buttons = new ArrayList<InlineKeyboardButton>();
        InlineKeyboardButton en= new InlineKeyboardButton("EN");
        en.setCallbackData("eng");
        Buttons.add(en);
        InlineKeyboardButton ru= new InlineKeyboardButton("RU");
        ru.setCallbackData("rus");
        Buttons.add(ru);
        InlineKeyboardButton fr= new InlineKeyboardButton("FR");
        fr.setCallbackData("fra");
        Buttons.add(fr);
        InlineKeyboardButton kz= new InlineKeyboardButton("KZ");
        kz.setCallbackData("kaz");
        Buttons.add(kz);
        keyboard.add(Buttons);
        inlineKeyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
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
        String pathName = "/app/src/photo_" + fileNameSuffix + ".jpg";
        try {
            java.io.File file1 = new java.io.File(pathName);
            return downloadFile(filePath, file1);
        } catch (TelegramApiException e) {

            e.printStackTrace();
        }
        return null;
    }


    public void tesseractPhotoCMD(java.io.File resultFile, Update update) throws IOException {
        StringBuilder strB = new StringBuilder();
        java.io.File outputFile = new java.io.File(resultFile.getParentFile(), "output");
        ProcessBuilder pb = new ProcessBuilder("tesseract",
                "--tessdata-dir",
                "/app/src/main/resources/tessdata/",
                resultFile.getPath(),
                outputFile.getName(),
                "-l",
                users.get(update.getMessage().getChatId().toString()));
        pb.directory(resultFile.getParentFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            process.waitFor();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(outputFile.getAbsolutePath() + ".txt"),
                    "UTF-8"));
            String str;

            while ((str = in.readLine()) != null)
            {
                strB.append(str).append(" ");
            }
            in.close();
            sendMessage(update.getMessage().getChatId(), strB.toString());
            sendInlineKeyboard(update);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
