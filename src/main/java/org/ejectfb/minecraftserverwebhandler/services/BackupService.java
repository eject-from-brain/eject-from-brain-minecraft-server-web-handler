package org.ejectfb.minecraftserverwebhandler.services;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ServerProperties serverProperties;
    private final ServerService serverService;

    @Autowired
    public BackupService(SimpMessagingTemplate messagingTemplate,
                         ServerProperties serverProperties,
                         ServerService serverService) {
        this.messagingTemplate = messagingTemplate;
        this.serverProperties = serverProperties;
        this.serverService = serverService;
    }

    public synchronized void createBackup() throws IOException {
        createBackup("manual");
    }

    public synchronized void createBackup(String type) throws IOException {
        // Получаем абсолютный путь к директории сервера
        Path serverDir = Path.of(new File(new File(serverProperties.getJar()).getPath()).getAbsoluteFile().getParent());
        Path backupDir = Paths.get(serverProperties.getBackup().getDirectory(), type).toAbsolutePath();

        // Нормализуем пути для корректного сравнения
        serverDir = serverDir.normalize();
        backupDir = backupDir.normalize();

        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = "backup_" + timestamp + ".zip";

        // Создаем директорию для бэкапов, если ее нет
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
                        // Исключаем файлы из самой папки бэкапов
                        return !path.startsWith(finalBackupDir);
                    })
                    .forEach(path -> {
                        try {
                            // Получаем относительный путь файла относительно serverDir
                            Path relativePath = finalServerDir.relativize(path);

                            // Создаем запись в ZIP с правильным путем
                            zos.putNextEntry(new ZipEntry(relativePath.toString().replace("\\", "/")));

                            // Копируем содержимое файла
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

        // Ежедневный бэкап
        if (serverProperties.getBackup().isDailyEnabled()) {
            createBackup("daily");
            cleanupOldBackups("daily", serverProperties.getBackup().getDailyMaxBackups());
        }

        // Еженедельный бэкап (в воскресенье)
        if (serverProperties.getBackup().isWeeklyEnabled() && now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            createBackup("weekly");
            cleanupOldBackups("weekly", serverProperties.getBackup().getWeeklyMaxBackups());
        }

        // Ежемесячный бэкап (в первый день месяца)
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