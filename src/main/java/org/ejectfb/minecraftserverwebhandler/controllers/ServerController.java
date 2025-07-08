package org.ejectfb.minecraftserverwebhandler.controllers;

import org.ejectfb.minecraftserverwebhandler.dto.ServerStats;
import org.ejectfb.minecraftserverwebhandler.services.ServerDataService;
import org.ejectfb.minecraftserverwebhandler.services.ServerService;
import org.ejectfb.minecraftserverwebhandler.services.TelegramBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
    public ServerController(ServerService serverService,
                            ServerDataService serverDataService,
                            TelegramBotService telegramBotService,
                            SimpMessagingTemplate messagingTemplate) {
        this.serverService = serverService;
        this.serverDataService = serverDataService;
        this.telegramBotService = telegramBotService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/start")
    public void startServer(@RequestParam String command) {
        try {
            serverDataService.setServerStartTime(System.currentTimeMillis());
            serverService.startServer(command);
            sendToConsole("Сервер запущен: " + command);

            if (telegramBotService != null) {
                telegramBotService.sendMessage("✅ Сервер Minecraft запущен");
            }
        } catch (IOException e) {
            sendToConsole("Ошибка запуска сервера: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public void stopServer() {
        serverService.stopServer();
        sendToConsole("Сервер остановлен");

        if (telegramBotService != null) {
            telegramBotService.sendMessage("⛔ Сервер Minecraft остановлен");
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
        return serverService.getStats();
    }

    @PostMapping("/telegram/test")
    public String testTelegramConnection(@RequestParam String token,
                                         @RequestParam String chatId) {
        try {
            boolean connected = telegramBotService.testConnection(token, chatId);
            if (connected) {
                telegramBotService.setBotToken(token);
                telegramBotService.setChatId(chatId);
            }
            sendToConsole(connected ? "Telegram бот подключен" : "Ошибка подключения Telegram бота");
            return connected ? "Connected" : "Connection failed";
        } catch (Exception e) {
            sendToConsole("Ошибка подключения Telegram: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/interval")
    public void setPollInterval(@RequestParam int hours) {
        if (hours > 0) {
            this.pollIntervalHours = hours;
            serverService.setPollInterval(hours);
            sendToConsole("Интервал опроса установлен: " + hours + " часов");
        }
    }

    @GetMapping("/status")
    public boolean getServerStatus() {
        return serverService.isServerRunning();
    }

    @PostMapping("/clear")
    public void clearConsole() {
        messagingTemplate.convertAndSend("/topic/console", "--- Консоль очищена ---");
    }

    private void sendToConsole(String message) {
        messagingTemplate.convertAndSend("/topic/console", message);
    }

    private void requestStats() {
        if (serverService.isServerRunning()) {
            try {
                serverService.sendCommand("list");
                serverService.sendCommand("tps");

                scheduler.schedule(() -> {
                    ServerStats stats = serverDataService.getStats();
                    sendToConsole(stats.toFormattedString());

                    if (telegramBotService != null) {
                        telegramBotService.sendMessage(stats.toFormattedString());
                    }
                }, 5, TimeUnit.SECONDS);
            } catch (IOException e) {
                sendToConsole("Ошибка запроса статистики: " + e.getMessage());
            }
        } else {
            sendToConsole("Сервер не запущен, статистика недоступна");
        }
    }
}