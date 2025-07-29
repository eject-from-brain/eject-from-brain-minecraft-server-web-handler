package org.ejectfb.minecraftserverwebhandler.services;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ConfigFileService {
    private final ServerProperties serverProperties;
    private final Yaml yaml;

    public ConfigFileService(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }

    public void saveConfigurationToFile() throws IOException {
        Path configPath = Paths.get("./application.yml");
        Path backupPath = Paths.get("./application.yml.bak");

        if (Files.exists(configPath)) {
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            String configContent = buildConfigContent();
            Files.writeString(configPath, configContent);
        } catch (IOException e) {
            if (Files.exists(backupPath)) {
                Files.copy(backupPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
            throw e;
        } finally {
            if (Files.exists(backupPath)) {
                Files.delete(backupPath);
            }
        }
    }

    public String buildConfigContent() {
        Map<String, Object> configMap = new LinkedHashMap<>();

        // Application
        Map<String, Object> springMap = new LinkedHashMap<>();
        springMap.put("application", Map.of("name", "minecraft-server-web-handler"));
        configMap.put("spring", springMap);

        // Telegram
        configMap.put("telegram", Map.of(
                "bot", Map.of(
                        "token", serverProperties.getTelegram().getBotToken()  == null ? "" : serverProperties.getTelegram().getBotToken(),
                        "chatId", serverProperties.getTelegram().getChatId()  == null ? "" : serverProperties.getTelegram().getChatId()
                )
        ));

        // Server
        Map<String, Object> serverMap = new LinkedHashMap<>();
        serverMap.put("port", serverProperties.getPort());
        serverMap.put("memory", Map.of(
                "xmx", serverProperties.getMemory().getXmx(),
                "xms", serverProperties.getMemory().getXms()
        ));
        serverMap.put("jar", serverProperties.getJar());
        serverMap.put("stats-poll-interval", serverProperties.getStatsPollInterval());
        serverMap.put("auto-run", serverProperties.isAutoRun());

        // Backup
        Map<String, Object> backupMap = new LinkedHashMap<>();
        backupMap.put("enabled", serverProperties.getBackup().isEnabled());
        backupMap.put("enableRestartNotifications", serverProperties.getBackup().isEnableRestartNotifications());
        backupMap.put("notificationTemplate", serverProperties.getBackup().getNotificationTemplate());
        backupMap.put("notificationTimes", serverProperties.getBackup().getNotificationTimes());
        backupMap.put("directory", serverProperties.getBackup().getDirectory());
        backupMap.put("backupTime", serverProperties.getBackup().getBackupTime());
        backupMap.put("dailyEnabled", serverProperties.getBackup().isDailyEnabled());
        backupMap.put("dailyMaxBackups", serverProperties.getBackup().getDailyMaxBackups());
        backupMap.put("weeklyEnabled", serverProperties.getBackup().isWeeklyEnabled());
        backupMap.put("weeklyMaxBackups", serverProperties.getBackup().getWeeklyMaxBackups());
        backupMap.put("monthlyEnabled", serverProperties.getBackup().isMonthlyEnabled());
        backupMap.put("monthlyMaxBackups", serverProperties.getBackup().getMonthlyMaxBackups());

        serverMap.put("backup", backupMap);
        configMap.put("server", serverMap);

        // Security
        configMap.put("security", Map.of(
                "user", Map.of(
                        "username", serverProperties.getSecurity().getUsername(),
                        "password", serverProperties.getSecurity().getPassword()
                )
        ));

        // Logging
        configMap.put("logging", Map.of(
                "level", Map.of(
                        "org", Map.of(
                                "springframework", Map.of(
                                        "web", "DEBUG"
                                )
                        )
                )
        ));

        return yaml.dump(configMap);
    }
}