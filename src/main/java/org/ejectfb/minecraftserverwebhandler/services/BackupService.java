package org.ejectfb.minecraftserverwebhandler.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
        stopBackupScheduler();
    }

    public void startBackupScheduler() {
        stopBackupScheduler();

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
        try {
            LocalDateTime now = LocalDateTime.now();
            sendToConsole("Starting scheduled backups at " + now);

            if (serverProperties.getBackup().isDailyEnabled()) {
                createBackup("daily");
                cleanupOldBackups("daily", serverProperties.getBackup().getDailyMaxBackups());
            }

            if (serverProperties.getBackup().isWeeklyEnabled() && now.getDayOfWeek() == DayOfWeek.SUNDAY) {
                createBackup("weekly");
                cleanupOldBackups("weekly", serverProperties.getBackup().getWeeklyMaxBackups());
            }

            if (serverProperties.getBackup().isMonthlyEnabled() && now.getDayOfMonth() == 1) {
                createBackup("monthly");
                cleanupOldBackups("monthly", serverProperties.getBackup().getMonthlyMaxBackups());
            }

            sendToConsole("All scheduled backups completed");
        } catch (Exception e) {
            sendToConsole("Error during scheduled backups: " + e.getMessage());
        }
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

    public synchronized void createBackup(String type) throws IOException {
        Path serverDir = Path.of(new File(new File(serverProperties.getJar()).getPath()).getAbsoluteFile().getParent());
        Path backupDir = Paths.get(serverProperties.getBackup().getDirectory(), type).toAbsolutePath();

        serverDir = serverDir.normalize();
        backupDir = backupDir.normalize();

        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = "backup_" + timestamp + ".zip";

        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        Path zipPath = backupDir.resolve(backupName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Path finalBackupDir = backupDir;
            Path finalServerDir = serverDir;
            Files.walk(serverDir)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        return !path.startsWith(finalBackupDir);
                    })
                    .forEach(path -> {
                        try {
                            Path relativePath = finalServerDir.relativize(path);

                            zos.putNextEntry(new ZipEntry(relativePath.toString().replace("\\", "/")));

                            Files.copy(path, zos);

                            zos.closeEntry();
                        } catch (IOException e) {
                            sendToConsole("Error adding file to backup: " + path + " - " + e.getMessage());
                        }
                    });
        }

        sendToConsole("Backup created: " + zipPath);
    }

    public void restoreBackup(String backupName, String type) throws IOException {
        String backupDir = serverProperties.getBackup().getDirectory() + File.separator + type;
        Path zipPath = Paths.get(backupDir, backupName);
        String serverDir = new File(new File(serverProperties.getJar()).getPath()).getAbsoluteFile().getParent();

        if (serverService.isServerRunning()) {
            serverService.stopServer();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = Paths.get(serverDir, entry.getName());
                Files.createDirectories(filePath.getParent());
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        sendToConsole("Backup restored: " + backupName);
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

    @Scheduled(cron = "${server.backup.backupTime:0 0 4 * * ?}")
    public void scheduledBackup() throws IOException {
        LocalDateTime now = LocalDateTime.now();

        if (serverProperties.getBackup().isDailyEnabled()) {
            createBackup("daily");
            cleanupOldBackups("daily", serverProperties.getBackup().getDailyMaxBackups());
        }

        if (serverProperties.getBackup().isWeeklyEnabled() && now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            createBackup("weekly");
            cleanupOldBackups("weekly", serverProperties.getBackup().getWeeklyMaxBackups());
        }

        if (serverProperties.getBackup().isMonthlyEnabled() && now.getDayOfMonth() == 1) {
            createBackup("monthly");
            cleanupOldBackups("monthly", serverProperties.getBackup().getMonthlyMaxBackups());
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

    private void sendToConsole(String message) {
        messagingTemplate.convertAndSend("/topic/console", message);
    }
}