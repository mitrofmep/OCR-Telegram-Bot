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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
    private HashMap<String, String> userLangSetup = new HashMap<>();

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
        if (update.hasMessage()) {
            Message message = update.getMessage();

            if (message.hasText()) {
                String input = message.getText();

                if (input.equals("/start")) {
                    SendMessage sendMessage = SendMessage
                            .builder()
                            .text("Что нужно сделать?")
                            .chatId(message.getChatId())
                            .replyMarkup(startCommandReplyMarkup())
                            .build();
                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (update.hasMessage() && update.getMessage().hasPhoto()) {
            if (!userLangSetup.containsKey(update.getMessage().getChatId().toString())) {
                sendMessage(update.getMessage().getChatId(), "У вас еще не выбран язык текста для оцифровки. " +
                        "Сначала выберите соответствующий пункт в меню по команде /start");
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
        else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            String[] data = callbackQuery.getData().split(":");
            InlineKeyboardMarkup markup = null;
            String editedText = null;
            if (data[0].equals("start")) {
                if (data[1].equals("book")) {
                    markup = bookPageReplyMarkup();
                    editedText = "Выберите магазин";
                }
                if (data[1].equals("ocr")) {
                    markup = ocrPageLangReplyMarkup();
                    editedText = "Выберите язык текста на вашем изображении";
                }
            } else if (data[0].equals("book")) {
                if (data[1].equals("back")) {
                    markup = startCommandReplyMarkup();
                    editedText = "Что нужно сделать?";
                }
            } else if (data[0].equals("ocr")) {
                if (data[1].equals("back")) {
                    markup = startCommandReplyMarkup();
                    editedText = "Что нужно сделать?";
                } else {
                    userLangSetup.put(update.getCallbackQuery().getFrom().getId().toString(), data[1]);
                    editedText = "Вы выбрали язык: " + data[1] +
                            ". Теперь отправьте мне скриншот с текстом, который требуется оцифровать. " +
                            "Либо вернитесь назад для выбора другого языка";
                    markup = ocrPagePhotoWaitReplyMarkup();
                }
            } else if (data[0].equals("ocr2")) {
                if (data[1].equals("back")) {
                    markup = ocrPageLangReplyMarkup();
                    editedText = "Выберите язык текста на вашем изображении";
                }
            }
            if (markup != null) {
                sendEditedMessage(editedText, markup, callbackQuery);
            }
        }
    }

    private void sendEditedMessage(String editedText, InlineKeyboardMarkup markup, CallbackQuery callbackQuery) {
        EditMessageText editMessageText = EditMessageText
                .builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .inlineMessageId(callbackQuery.getInlineMessageId())
                .text(editedText)
                .messageId(callbackQuery.getMessage().getMessageId())
                .replyMarkup(markup)
                .build();
        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private InlineKeyboardMarkup startCommandReplyMarkup() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        rowInline.add(InlineKeyboardButton.builder().text("\uD83D\uDCC7Перевести изображение в текст").callbackData("start:ocr").build());
        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        rowInline2.add(InlineKeyboardButton.builder().text("\uD83D\uDCD6Купить книгу").callbackData("start:book").build());
        rowsInline.add(rowInline);
        rowsInline.add(rowInline2);
        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    private InlineKeyboardMarkup ocrPageLangReplyMarkup() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        rowInline.add(InlineKeyboardButton.builder().text("Английский(ENG)").callbackData("ocr:eng").build());
        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        rowInline2.add(InlineKeyboardButton.builder().text("Русский(RUS)").callbackData("ocr:rus").build());
        List<InlineKeyboardButton> rowInline3 = new ArrayList<>();
        rowInline3.add(InlineKeyboardButton.builder().text("⬅️Назад").callbackData("ocr:back").build());
        rowsInline.add(rowInline);
        rowsInline.add(rowInline2);
        rowsInline.add(rowInline3);
        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    private InlineKeyboardMarkup ocrPagePhotoWaitReplyMarkup() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        rowInline.add(InlineKeyboardButton.builder().text("⬅️Назад").callbackData("ocr2:back").build());
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    private InlineKeyboardMarkup bookPageReplyMarkup() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        rowInline.add(InlineKeyboardButton.builder().text("Book 24").url("https://book24.ru/r/UoTMv").build());
        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        rowInline2.add(InlineKeyboardButton.builder().text("Читай-Город").url("https://www.chitai-gorod.ru/r/hoUBH").build());
        List<InlineKeyboardButton> rowInline3 = new ArrayList<>();
        rowInline3.add(InlineKeyboardButton.builder().text("Буквоед").url("https://www.bookvoed.ru/book?id=13564138").build());
        List<InlineKeyboardButton> rowInline4 = new ArrayList<>();
        rowInline4.add(InlineKeyboardButton.builder().text("Лабиринт").url("https://www.labirint.ru/books/920691/").build());
        List<InlineKeyboardButton> rowInline5 = new ArrayList<>();
        rowInline5.add(InlineKeyboardButton.builder().text("⬅️Назад").callbackData("book:back").build());
        rowsInline.add(rowInline);
        rowsInline.add(rowInline2);
        rowsInline.add(rowInline3);
        rowsInline.add(rowInline4);
        rowsInline.add(rowInline5);
        markupInline.setKeyboard(rowsInline);
        return markupInline;
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
                userLangSetup.get(update.getMessage().getChatId().toString()));
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
            SendMessage sendMessage = SendMessage
                    .builder()
                    .text("Что нужно сделать?")
                    .chatId(update.getMessage().getChatId())
                    .replyMarkup(startCommandReplyMarkup())
                    .build();
            try {

                execute(sendMessage);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
