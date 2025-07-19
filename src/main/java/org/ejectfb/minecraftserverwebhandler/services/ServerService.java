package org.ejectfb.minecraftserverwebhandler.services;

import jakarta.annotation.PreDestroy;
import org.ejectfb.minecraftserverwebhandler.dto.ServerStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ServerService {
    private Process serverProcess;
    private BufferedWriter processWriter;
    private final ServerDataService dataService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isServerRunning = false;
    private volatile boolean userRequestedStop = false;
    private int pollIntervalHours = 3;
    private Timer statsTimer;
    private String serverCommand;

    @PreDestroy
    public void cleanup() {
        if (isServerRunning) {
            System.out.println("Application is closing, stopping Minecraft server...");
            stopServer();
        }
    }

    @Autowired
    private TelegramBotService telegramBotService;


    public ServerService(ServerDataService dataService, SimpMessagingTemplate messagingTemplate) {
        this.dataService = dataService;
        this.messagingTemplate = messagingTemplate;
    }

    public synchronized void startServer(String command) throws IOException {
        this.serverCommand = command;
        if (isServerRunning) {
            throw new IllegalStateException("Server is already running");
        }
        userRequestedStop = false;
        dataService.reset();
        dataService.setServerStartTime(System.currentTimeMillis());

        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.command("cmd", "/c", command);
        } else {
            pb.command("/bin/sh", "-c", command);
        }

        pb.redirectErrorStream(true);
        pb.environment().put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");
        serverProcess = pb.start();

        Executors.newSingleThreadExecutor().submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    messagingTemplate.convertAndSend("/topic/console", line);
                    dataService.parseConsoleLine(line);
                }
                handleServerStopped();
            } catch (IOException e) {
                messagingTemplate.convertAndSend("/topic/console",
                        "Error reading server output: " + e.getMessage());
            } finally {
                handleServerStopped();
            }
        });

        processWriter = new BufferedWriter(
                new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));

        isServerRunning = true;
        startStatsTimer();
        sendToConsole("Server started with command: " + command);
    }

    public synchronized void stopServer() {
        if (!isServerRunning || serverProcess == null) return;
        userRequestedStop = true;

        try {
            sendCommand("stop");
            scheduler.schedule(() -> {
                if (serverProcess.isAlive()) {
                    serverProcess.destroyForcibly();
                }
            }, 5, TimeUnit.SECONDS);

            sendToConsole("Server stop command sent");
        } catch (IOException e) {
            sendToConsole("Error sending stop command: " + e.getMessage());
            serverProcess.destroyForcibly();
        }
    }

    public synchronized void sendCommand(String command) throws IOException {
        if (!isServerRunning || processWriter == null) {
            throw new IllegalStateException("Server is not running");
        }

        processWriter.write(command + "\n");
        processWriter.flush();
        sendToConsole("> " + command);
    }

    public ServerStats getStats() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return new ServerStats(
                dataService.getOnlinePlayers(),
                dataService.getTps(),
                dataService.getMemory(),
                dataService.getUpTime(),
                dtf.format(LocalDateTime.now()),
                isServerRunning ? "Running" : "Stopped"
        );
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

    public void setPollInterval(int hours) {
        this.pollIntervalHours = hours;
        startStatsTimer();
    }

    private void startStatsTimer() {
        if (statsTimer != null) {
            statsTimer.cancel();
        }

        statsTimer = new Timer();
        statsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isServerRunning) {
                    try {
                        sendCommand("list");
                        sendCommand("tps");
                        Thread.sleep(5000);
                        sendStatsToConsole();
                        telegramBotService.sendServerStats(getStats());
                    } catch (Exception e) {
                        sendToConsole("Ошибка при отправке статистики: " + e.getMessage());
                    }
                }
            }
        }, pollIntervalHours * 3600 * 1000L, pollIntervalHours * 3600 * 1000L);
    }

    private void handleServerStopped() {
        if (isServerRunning) {
            isServerRunning = false;
            sendToConsole("Server stopped");

            if (!userRequestedStop && telegramBotService != null) {
                telegramBotService.sendServerStopNotification();
            }

            if (!userRequestedStop) {
                scheduler.schedule(() -> {
                    try {
                        clearConsole();
                        telegramBotService.sendServerRestartNotification();
                        startServer(serverCommand);
                    } catch (IOException e) {
                        sendToConsole("Failed to restart server: " + e.getMessage());
                    }
                }, 5, TimeUnit.SECONDS);
            } else {
                userRequestedStop = false;
            }

            cleanupResources();
        }
    }

    private void cleanupResources() {
        try {
            if (processWriter != null) {
                processWriter.close();
                processWriter = null;
            }
        } catch (IOException e) {
            sendToConsole("Error closing process writer: " + e.getMessage());
        }
    }

    private void sendToConsole(String message) {
        messagingTemplate.convertAndSend("/topic/console", message);
    }

    private void sendStatsToConsole() {
        ServerStats stats = getStats();
        String statsMessage = String.format(
                "Server Stats [%s]%nPlayers: %s%nTPS: %s%nMemory: %s%nUptime: %s",
                stats.timestamp(), stats.onlinePlayers(),
                stats.tps(), stats.memory(), stats.upTime()
        );
        sendToConsole(statsMessage);
    }

    private void clearConsole() {
        messagingTemplate.convertAndSend("/topic/console", "clear");
    }

    public String getServerCommand() {
        return this.serverCommand;
    }

    public String getServerLogs() {
        if (!isServerRunning || serverProcess == null) {
            return "";
        }

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8)
            );

            StringBuilder logs = new StringBuilder();
            String line;
            while (reader.ready() && (line = reader.readLine()) != null) {
                logs.append(line).append("\n");
            }
            return logs.toString();
        } catch (IOException e) {
            return "Error reading logs: " + e.getMessage();
        }
    }
}