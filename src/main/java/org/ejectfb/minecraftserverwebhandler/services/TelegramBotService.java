package org.ejectfb.minecraftserverwebhandler.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class TelegramBotService {
    private String botToken;
    private String chatId;

    public TelegramBotService(@Value("${telegram.bot.token:}") String botToken,
                              @Value("${telegram.bot.chatId:}") String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
    }

    public boolean isBotConnected() {
        try {
            sendMessage("✅ Проверка соединения: бот успешно подключен!");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Новый метод для тестирования соединения с другими параметрами
    public boolean testConnection(String testBotToken, String testChatId) {
        if (testBotToken == null || testBotToken.isEmpty() || testChatId == null || testChatId.isEmpty()) {
            return false;
        }

        try {
            String urlString = "https://api.telegram.org/bot" + testBotToken + "/getMe";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString().contains("\"ok\":true");
            }
        } catch (IOException e) {
            return false;
        }
    }

    public void sendMessage(String message) {
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                HttpURLConnection conn = prepareConnection(message);
                checkResponse(conn);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private HttpURLConnection prepareConnection(String text) throws IOException {
        String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String jsonInputString = String.format(
                "{\"chat_id\": \"%s\", \"text\": \"%s\"}",
                chatId,
                text.replace("\"", "\\\"")
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return conn;
    }

    private void checkResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            if (!response.toString().contains("\"ok\":true")) {
                throw new IOException("Telegram API error: " + response);
            }
        }
    }

    // Сеттеры для обновления токена и chatId
    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }
}