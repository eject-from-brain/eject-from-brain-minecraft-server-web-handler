package org.ejectfb.minecraftserverwebhandler.services;

import org.ejectfb.minecraftserverwebhandler.dto.ServerStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TelegramBotService {
    private String botToken;
    private String chatId;

    public TelegramBotService(@Value("${telegram.bot.token:}") String botToken,
                              @Value("${telegram.bot.chatId:}") String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
    }

    public boolean testConnection(String testToken, String testChatId) {
        if (testToken == null || testToken.isEmpty() || testChatId == null || testChatId.isEmpty()) {
            return false;
        }

        try {
            String urlString = "https://api.telegram.org/bot" + testToken + "/getMe";
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
                boolean isOk = response.toString().contains("\"ok\":true");
                if (isOk) {
                    String testMessage = "✅ Проверка соединения: бот успешно подключен!";
                    sendTestMessage(testToken, testChatId, testMessage);
                }

                return isOk;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private void sendTestMessage(String token, String chatId, String message) {
        try {
            String urlString = "https://api.telegram.org/bot" + token + "/sendMessage";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInputString = String.format(
                    "{\"chat_id\": \"%s\", \"text\": \"%s\"}",
                    chatId,
                    message.replace("\"", "\\\"")
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            }
        } catch (IOException e) {
            System.err.println("Ошибка отправки тестового сообщения: " + e.getMessage());
        }
    }

    public boolean sendServerStartingNotification() {
        String message = "🚀 Сервер Minecraft запуcкается...\n" +
                "⏰ Время инициализации: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return sendMessage(message);
    }

    public boolean sendServerStartedNotification() {
        String message = "✅ Сервер Minecraft запущен\n" +
                "⏰ Время запуска: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return sendMessage(message);
    }

    public boolean sendServerRestartNotification() {
        String message = "🔃 Сервер Minecraft переазпускается\n" +
                "⏰ Время перезапуска: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return sendMessage(message);
    }

    public boolean sendServerStopNotification() {
        String message = "⛔ Сервер Minecraft остановлен\n" +
                "⏰ Время остановки: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return sendMessage(message);
    }

    public boolean sendServerStats(ServerStats stats) {
        String serverIp;
        try {
            URL externalIp = new URL("http://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(externalIp.openStream()));
            serverIp = in.readLine();
        } catch (Exception e) {
            serverIp = "N/A";
        }

        String message = String.format(
                """
                📊 Статистика сервера Minecraft (%s)
                🌐 IP: %s
                🔄 Состояние: %s
                🧮 Память: %s
                👥 Онлайн: %s
                ⏱ TPS: %s
                ⏳ Время работы: %s""",
                stats.timestamp(),
                serverIp,
                stats.status().equals("Running") ? "работает" : "остановлен",
                stats.memory(),
                stats.onlinePlayers(),
                stats.tps(),
                stats.upTime()
        );
        return sendMessage(message);
    }

    public boolean sendMessage(String text) {
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            return false;
        }

        try {
            HttpURLConnection conn = prepareConnection(text);
            return checkResponse(conn);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private HttpURLConnection prepareConnection(String text) throws IOException {
        String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String escapedText = text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String jsonInputString = String.format(
                "{\"chat_id\": \"%s\", \"text\": \"%s\", \"parse_mode\": \"Markdown\"}",
                chatId,
                escapedText
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return conn;
    }

    private boolean checkResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return br.lines().anyMatch(line -> line.contains("\"ok\":true"));
        }
    }

    public String getBotToken() {
        return this.botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getChatId() {
        return this.chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }
}