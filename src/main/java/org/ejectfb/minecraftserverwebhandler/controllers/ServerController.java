package org.ejectfb.minecraftserverwebhandler.controllers;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.ejectfb.minecraftserverwebhandler.dto.ServerStats;
import org.ejectfb.minecraftserverwebhandler.services.ServerDataService;
import org.ejectfb.minecraftserverwebhandler.services.ServerService;
import org.ejectfb.minecraftserverwebhandler.services.TelegramBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/server")
public class ServerController {
    private final ServerService serverService;
    private final ServerDataService serverDataService;
    private final TelegramBotService telegramBotService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private int pollIntervalHours = 3;

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    public ServerController(ServerService serverService,
                            ServerDataService serverDataService,
                            TelegramBotService telegramBotService,
                            SimpMessagingTemplate messagingTemplate) {
        this.serverService = serverService;
        this.serverDataService = serverDataService;
        this.telegramBotService = telegramBotService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/settings/default")
    public Map<String, Object> getDefaultSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("xmx", serverProperties.getMemory().getXmx());
        settings.put("xms", serverProperties.getMemory().getXms());
        settings.put("jar", serverProperties.getJar());
        settings.put("pollInterval", serverProperties.getStatsPollInterval());
        return settings;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startServer(@RequestParam String command) {
        try {
            serverDataService.setServerStartTime(System.currentTimeMillis());
            serverService.startServer(command);
            sendToConsole("Сервер запущен: " + command);

            if (telegramBotService != null) {
                boolean sent = telegramBotService.sendServerStartNotification();
                return ResponseEntity.ok(sent ? "Server started with Telegram notification"
                        : "Server started but Telegram notification failed");
            }
            return ResponseEntity.ok("Server started without Telegram notification");
        } catch (IOException e) {
            sendToConsole("Ошибка запуска сервера: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error starting server: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public void stopServer() {
        serverService.stopServer();
        sendToConsole("Сервер остановлен");

        if (telegramBotService != null) {
            telegramBotService.sendServerStopNotification();
        }
    }

    @Scheduled(fixedRate = 3 * 60 * 60 * 1000)
    public void sendScheduledStats() {
        if (serverService.isServerRunning()) {
            ServerStats stats = serverService.getStats();
            if (telegramBotService != null) {
                telegramBotService.sendServerStats(stats);
            }
            sendToConsole("Scheduled stats sent to Telegram");
        }
    }

    @PostMapping("/restart")
    public void restartServer() {
        if (serverService.isServerRunning()) {
            sendToConsole("Перезапуск сервера...");
            serverService.stopServer();

            scheduler.schedule(() -> {
                try {
                    serverService.startServer(serverService.getServerCommand());
                } catch (IOException e) {
                    sendToConsole("Ошибка при перезапуске: " + e.getMessage());
                }
            }, 5, TimeUnit.SECONDS);
        }
    }

    @PostMapping("/command")
    public void sendCommand(@RequestParam String command) {
        try {
            serverService.sendCommand(command);
            sendToConsole("> " + command);
        } catch (IOException e) {
            sendToConsole("Ошибка отправки команды: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ServerStats getStats() {
        sendStatsToTelegram();
        return serverService.getStats();
    }

    @PostMapping("/send-stats")
    public ResponseEntity<String> sendStatsToTelegram() {
        if (!serverService.isServerRunning()) {
            return ResponseEntity.badRequest().body("Сервер не запущен");
        }

        try {
            requestStats();
            Thread.sleep(5000);
            ServerStats stats = serverService.getStats();
            boolean sent = telegramBotService.sendServerStats(stats);
            if (sent) {
                return ResponseEntity.ok("Статистика отправлена в Telegram");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Ошибка отправки статистики");
            }
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка: " + e.getMessage());
        }
    }

    private void requestStats() throws IOException {
        serverService.sendCommand("list");
        serverService.sendCommand("tps");
    }

    @GetMapping("/telegram/settings")
    public ResponseEntity<Map<String, String>> getTelegramSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("token", telegramBotService.getBotToken());
        settings.put("chatId", telegramBotService.getChatId());
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/telegram/test")
    public ResponseEntity<String> testTelegramConnection(
            @RequestParam String token,
            @RequestParam String chatId) {

        try {
            boolean isConnected = telegramBotService.testConnection(token, chatId);

            if (isConnected) {
                telegramBotService.setBotToken(token);
                telegramBotService.setChatId(chatId);

                return ResponseEntity.ok("""
                Проверка соединения успешна!
                В чат Telegram должно было прийти тестовое сообщение.
                Токен и chatId сохранены.""");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Ошибка подключения к Telegram API. Проверьте токен и chatId.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка: " + e.getMessage());
        }
    }

    @PostMapping("/interval")
    public void setPollInterval(@RequestParam int hours) {
        if (hours > 0) {
            this.pollIntervalHours = hours;
            serverService.setPollInterval(hours);
            sendToConsole("New poll interval: " + hours + " hours");
        }
    }

    @GetMapping("/status")
    public boolean getServerStatus() {
        return serverService.isServerRunning();
    }

    @PostMapping("/clear")
    public void clearConsole() {
        messagingTemplate.convertAndSend("/topic/console", "clear");
    }

    @PostMapping("/settings")
    public ResponseEntity<String> saveSettings(@RequestBody Map<String, String> settings) {
        try {
            String command = settings.get("command");
            int interval = Integer.parseInt(settings.get("pollInterval"));

            if (interval > 0) {
                this.pollIntervalHours = interval;
                serverService.setPollInterval(interval);
            }

            if (command != null && !command.isEmpty()) {
                serverService.setServerCommand(command);
            }

            return ResponseEntity.ok("Settings saved successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving settings: " + e.getMessage());
        }
    }

    private void sendToConsole(String message) {
        messagingTemplate.convertAndSend("/topic/console", message);
    }
}