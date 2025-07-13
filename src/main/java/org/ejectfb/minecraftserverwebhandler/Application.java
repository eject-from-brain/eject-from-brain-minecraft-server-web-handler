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
                String defaultConfig = ConfigFileService.generateConfigContent(
                        "",
                        "",
                        8080,
                        8,
                        1,
                        "server.jar",
                        3,
                        "admin",
                        "admin");
                Files.writeString(configPath, defaultConfig);
            }
        } catch (IOException e) {
            System.err.println("Failed to handle config file: " + e.getMessage());
        }
    }
}