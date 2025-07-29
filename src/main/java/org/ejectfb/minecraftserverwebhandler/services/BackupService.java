package org.ejectfb.minecraftserverwebhandler.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ServerProperties serverProperties;
    private final ServerService serverService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> backupTask;
    private final List<ScheduledFuture<?>> notificationTasks = new ArrayList<>();

    @Autowired
    TelegramBotService telegramBotService;

    @Autowired
    public BackupService(SimpMessagingTemplate messagingTemplate,
                         ServerProperties serverProperties,
                         ServerService serverService) {
        this.messagingTemplate = messagingTemplate;
        this.serverProperties = serverProperties;
        this.serverService = serverService;
    }

    @PostConstruct
    public void init() {
        startBackupScheduler();
    }

    @PreDestroy
    public void cleanup() {
        cancelPendingNotifications();
        stopBackupScheduler();
    }

    public void startBackupScheduler() {
        stopBackupScheduler();

        if (!serverProperties.getBackup().isEnabled()) {
            sendToConsole("Backup scheduler is disabled in settings");
            return;
        }

        int backupHour = parseHourFromTime(serverProperties.getBackup().getBackupTime());
        int backupMinute = parseMinuteFromTime(serverProperties.getBackup().getBackupTime());

        long initialDelay = calculateInitialDelay(backupHour, backupMinute);

        backupTask = scheduler.scheduleAtFixedRate(
                this::performScheduledBackups,
                initialDelay,
                24 * 60 * 60 * 1000L, // 24 часа
                TimeUnit.MILLISECONDS
        );

        sendToConsole("Backup scheduler started. Next backup at: " +
                LocalDateTime.now().plus(initialDelay, ChronoUnit.MILLIS));
    }

    public void stopBackupScheduler() {
        if (backupTask != null) {
            backupTask.cancel(false);
            sendToConsole("Backup scheduler stopped");
        }
    }

    private void performScheduledBackups() {
        LocalDateTime now = LocalDateTime.now();
        sendToConsole("Starting scheduled backup procedure at " + now);
        telegramBotService.sendMessage("⏰ Начало планового создания бэкапов");

        CompletableFuture<Void> backupChain = CompletableFuture.completedFuture(null);

        if (serverProperties.getBackup().isDailyEnabled()) {
            backupChain = backupChain.thenCompose(v ->
                    createBackup("daily")
                            .thenRun(() -> {
                                try {
                                    cleanupOldBackups("daily", serverProperties.getBackup().getDailyMaxBackups());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .exceptionally(e -> {
                                sendToConsole("⚠️ Daily backup failed: " + e.getMessage());
                                return null;
                            })
            );
        }

        if (serverProperties.getBackup().isWeeklyEnabled() && now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            backupChain = backupChain.thenCompose(v ->
                    createBackup("weekly")
                            .thenRun(() -> {
                                try {
                                    cleanupOldBackups("weekly", serverProperties.getBackup().getWeeklyMaxBackups());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .exceptionally(e -> {
                                sendToConsole("⚠️ Weekly backup failed: " + e.getMessage());
                                return null;
                            })
            );
        }

        if (serverProperties.getBackup().isMonthlyEnabled() && now.getDayOfMonth() == 1) {
            backupChain = backupChain.thenCompose(v ->
                    createBackup("monthly")
                            .thenRun(() -> {
                                try {
                                    cleanupOldBackups("monthly", serverProperties.getBackup().getMonthlyMaxBackups());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .exceptionally(e -> {
                                sendToConsole("⚠️ Monthly backup failed: " + e.getMessage());
                                return null;
                            })
            );
        }

        backupChain
                .thenRun(() -> {
                    sendToConsole("All scheduled backups completed");
                })
                .exceptionally(e -> {
                    sendToConsole("Some backups failed: " + e.getMessage());
                    telegramBotService.sendMessage("⚠️ Некоторые бэкапы не были созданы: " + e.getMessage());
                    return null;
                });
    }

    private long calculateInitialDelay(int targetHour, int targetMinute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(targetHour).withMinute(targetMinute).withSecond(0);

        if (now.compareTo(nextRun) > 0) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMillis();
    }

    private int parseHourFromTime(String timeStr) {
        try {
            if (timeStr.contains(":")) { // Формат HH:mm из UI
                return Integer.parseInt(timeStr.split(":")[0]);
            } else { // Cron-формат "0 0 4 * * ?"
                return Integer.parseInt(timeStr.split(" ")[2]);
            }
        } catch (Exception e) {
            return 4;
        }
    }

    private int parseMinuteFromTime(String timeStr) {
        try {
            if (timeStr.contains(":")) { // Формат HH:mm из UI
                return Integer.parseInt(timeStr.split(":")[1]);
            } else { // Cron-формат "0 0 4 * * ?"
                return Integer.parseInt(timeStr.split(" ")[1]);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public CompletableFuture<Void> createBackup(String type) {
        CompletableFuture<Void> backupFuture = new CompletableFuture<>();

        if (!serverService.isServerRunning()) {
            try {
                performBackupCreation(type);

                backupFuture.complete(null);
            } catch (IOException e) {
                backupFuture.completeExceptionally(e);
            }
            return backupFuture;
        }

        serverService.stopServer();

        serverService.getServerStopFuture().thenRunAsync(() -> {
            try {
                performBackupCreation(type);

                telegramBotService.sendServerStartingNotification();
                serverService.startServer(serverService.getServerCommand());

                backupFuture.complete(null);
            } catch (Exception e) {
                handleBackupError(e, type);
                backupFuture.completeExceptionally(e);

                try {
                    serverService.startServer(serverService.getServerCommand());
                    telegramBotService.sendServerBackupCreatingFailedNotification(e.getMessage());
                } catch (IOException ex) {
                    sendToConsole("❌ Failed to restart server after backup error: " + ex.getMessage());
                }
            }
        }, scheduler);

        return backupFuture;
    }

    private void performBackupCreation(String type) throws IOException {
        Path serverDir = Path.of(new File(new File(serverProperties.getJar()).getPath()).getAbsoluteFile().getParent());
        Path backupDir = Paths.get(serverProperties.getBackup().getDirectory(), type).toAbsolutePath();

        serverDir = serverDir.normalize();
        backupDir = backupDir.normalize();

        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = "backup_" + timestamp + ".zip";
        telegramBotService.sendServerBackupCreatingNotification(backupName);

        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        Path zipPath = backupDir.resolve(backupName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Path finalBackupDir = backupDir;
            Path finalServerDir = serverDir;
            Files.walk(serverDir)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.startsWith(finalBackupDir)) // Исключаем саму папку с бэкапами
                    .forEach(path -> {
                        try {
                            Path relativePath = finalServerDir.relativize(path);
                            zos.putNextEntry(new ZipEntry(relativePath.toString().replace("\\", "/")));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            sendToConsole("⚠️ Error adding file to backup: " + path + " - " + e.getMessage());
                        }
                    });
        }

        sendToConsole("Backup created: " + zipPath);
        telegramBotService.sendServerBackupCreatedNotification(backupName);
    }

    private void handleBackupError(Exception e, String type) {
        sendToConsole("Backup creation failed for " + type + ": " + e.getMessage());
        telegramBotService.sendMessage("❌ Ошибка создания бэкапа типа " + type + ": " + e.getMessage());

        if (e instanceof UncheckedIOException) {
            sendToConsole("File operation error: " + e.getCause().getMessage());
        }
    }

    public CompletableFuture<Void> restoreBackup(String backupName, String type) {
        CompletableFuture<Void> restoreFuture = new CompletableFuture<>();

        if (serverService.isServerRunning()) {
            telegramBotService.sendServerBackupRestoringNotification(backupName);
            sendToConsole("Starting backup restore procedure");

            serverService.stopServer();

            serverService.getServerStopFuture().thenRunAsync(() -> {
                try {
                    performBackupRestoration(backupName, type);

                    serverService.startServer(serverService.getServerCommand());
                    telegramBotService.sendServerStartingNotification();

                    restoreFuture.complete(null);
                } catch (Exception e) {
                    handleRestoreError(e, backupName);
                    restoreFuture.completeExceptionally(e);
                }
            }, scheduler);
        } else {
            try {
                performBackupRestoration(backupName, type);
                restoreFuture.complete(null);
            } catch (Exception e) {
                handleRestoreError(e, backupName);
                restoreFuture.completeExceptionally(e);
            }
        }

        telegramBotService.sendServerStartingNotification();
        return restoreFuture;
    }

    private void performBackupRestoration(String backupName, String type) throws IOException {
        String backupDir = serverProperties.getBackup().getDirectory() + File.separator + type;
        Path zipPath = Paths.get(backupDir, backupName);
        String serverDir = new File(new File(serverProperties.getJar()).getPath()).getAbsoluteFile().getParent();

        if (!Files.exists(zipPath)) {
            throw new FileNotFoundException("Backup file not found: " + zipPath);
        }

        Path tempDir = Files.createTempDirectory("mc_restore_");
        try {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path filePath = tempDir.resolve(entry.getName());
                    Files.createDirectories(filePath.getParent());
                    if (!entry.isDirectory()) {
                        Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            Files.walk(tempDir)
                    .forEach(source -> {
                        try {
                            Path destination = Paths.get(serverDir, tempDir.relativize(source).toString());
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(destination);
                            } else {
                                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

            sendToConsole("Backup restored: " + backupName);
            telegramBotService.sendServerBackupRestoredNotification(backupName);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                sendToConsole("Warning: Failed to delete temp directory: " + e.getMessage());
            }
        }
    }

    private void handleRestoreError(Exception e, String backupName) {
        sendToConsole("Backup restore failed: " + e.getMessage());
        telegramBotService.sendMessage("❌ Ошибка восстановления бэкапа " + backupName + ": " + e.getMessage());

        if (e instanceof UncheckedIOException) {
            sendToConsole("File operation error: " + e.getCause().getMessage());
        }
    }

    public void deleteBackup(String backupName, String type) throws IOException {
        String backupDir = serverProperties.getBackup().getDirectory() + File.separator + type;
        Path backupPath = Paths.get(backupDir, backupName);

        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("Backup file not found: " + backupPath);
        }

        Files.delete(backupPath);
        sendToConsole("Backup deleted: " + backupName);
    }

    private void cleanupOldBackups(String type, int maxBackups) throws IOException {
        String backupDir = String.valueOf(Paths.get(serverProperties.getBackup().getDirectory(), type));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(backupDir), "backup_*.zip")) {
            List<Path> backups = new ArrayList<>();
            stream.forEach(backups::add);

            if (backups.size() > maxBackups) {
                backups.sort(Comparator.comparingLong(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException e) {
                        return 0;
                    }
                }));

                for (int i = 0; i < backups.size() - maxBackups; i++) {
                    Files.delete(backups.get(i));
                    sendToConsole("Deleted old backup: " + backups.get(i).getFileName());
                }
            }
        }
    }

    public List<String> listBackups(String type) throws IOException {
        Path backupDirPath = Path.of(serverProperties.getBackup().getDirectory(), type);
        List<String> backups = new ArrayList<>();

        if(Files.exists(backupDirPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirPath, "backup_*.zip")) {
                for (Path path : stream) {
                    backups.add(path.getFileName().toString());
                }
            }

            backups.sort(Comparator.reverseOrder());
        }
        return backups;
    }

    public void scheduleRestartNotifications(LocalDateTime restartTime) {
        cancelPendingNotifications();

        if (!serverProperties.getBackup().isEnableRestartNotifications() ||
                !serverProperties.getBackup().isEnabled()) {
            return;
        }

        String[] times = serverProperties.getBackup().getNotificationTimes().split(",");
        String template = serverProperties.getBackup().getNotificationTemplate();

        for (String timeStr : times) {
            timeStr = timeStr.trim();
            try {
                long delay = parseTimeToMillis(timeStr);
                if (delay > 0) {
                    String finalTimeStr = timeStr;
                    ScheduledFuture<?> task = scheduler.schedule(() -> {
                                try {
                                    String message = template.replace("{time}", finalTimeStr);
                                    serverService.sendCommand("say " + message);
                                } catch (IOException e) {
                                    sendToConsole("Error sending notification: " + e.getMessage());
                                }
                            }, Duration.between(LocalDateTime.now(), restartTime).toMillis() - delay,
                            TimeUnit.MILLISECONDS);

                    notificationTasks.add(task);
                }
            } catch (Exception e) {
                sendToConsole("Invalid notification time format: " + timeStr);
            }
        }
    }

    private void cancelPendingNotifications() {
        for (ScheduledFuture<?> task : notificationTasks) {
            if (!task.isDone()) {
                task.cancel(false);
            }
        }
        notificationTasks.clear();
    }

    private long parseTimeToMillis(String timeStr) throws Exception {
        timeStr = timeStr.toLowerCase();
        if (timeStr.endsWith("h")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 3600 * 1000;
        } else if (timeStr.endsWith("m")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 60 * 1000;
        }
        throw new Exception("Invalid time format - use 'h' for hours or 'm' for minutes");
    }

    private void sendToConsole(String message) {
        messagingTemplate.convertAndSend("/topic/console", message);
    }
}