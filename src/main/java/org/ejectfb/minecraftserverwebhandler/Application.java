package org.ejectfb.minecraftserverwebhandler;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.ejectfb.minecraftserverwebhandler.services.AutoRunService;
import org.ejectfb.minecraftserverwebhandler.services.ConfigFileService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableConfigurationProperties
public class Application {
    public static void main(String[] args) {
        checkAndCreateConfigFile();
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);

        AutoRunService autoRunService = context.getBean(AutoRunService.class);
        autoRunService.autoStartIfEnabled();

        ServerProperties properties = context.getBean(ServerProperties.class);
        System.out.println("Loaded settings: Xmx=" + properties.getMemory().getXmx() +
                ", Xms=" + properties.getMemory().getXms() +
                ", AutoRun=" + properties.isAutoRun());
    }

    private static void checkAndCreateConfigFile() {
        Path configPath = Paths.get("./application.properties");
        try {
            if (!Files.exists(configPath)) {
                ServerProperties serverProperties = new ServerProperties();

                serverProperties.setTelegram(new ServerProperties.Telegram());
                serverProperties.getTelegram().setBotToken("");
                serverProperties.getTelegram().setChatId("");

                serverProperties.setPort(8080);

                serverProperties.setMemory(new ServerProperties.Memory());
                serverProperties.getMemory().setXms(1);
                serverProperties.getMemory().setXmx(8);

                serverProperties.setJar("server.jar");
                serverProperties.setStatsPollInterval(3);
                serverProperties.setAutoRun(false);

                serverProperties.setSecurity(new ServerProperties.Security());
                serverProperties.getSecurity().setUsername("admin");
                serverProperties.getSecurity().setPassword("admin");

                serverProperties.setBackup(new ServerProperties.Backup());
                serverProperties.getBackup().setDirectory("backups");
                serverProperties.getBackup().setMaxBackups(5);
                serverProperties.getBackup().setBackupTime("0 0 4 * * ?");
                serverProperties.getBackup().setIntervalHours(24);
                serverProperties.getBackup().setEnabled(false);

                String defaultConfig = ConfigFileService.generateConfigContent(serverProperties);
                Files.writeString(configPath, defaultConfig);
            }
        } catch (IOException e) {
            System.err.println("Failed to handle config file: " + e.getMessage());
        }
    }
}