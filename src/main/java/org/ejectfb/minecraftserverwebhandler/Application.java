package org.ejectfb.minecraftserverwebhandler;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
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
        ServerProperties properties = context.getBean(ServerProperties.class);
        System.out.println("Loaded settings: Xmx=" + properties.getMemory().getXmx() +
                ", Xms=" + properties.getMemory().getXms());
    }

    private static void checkAndCreateConfigFile() {
        Path configPath = Paths.get("./application.properties");

        try {
            if (!Files.exists(configPath)) {
                String defaultConfig = """
                spring.application.name=minecraft-server-web-handler
                # Telegram
                telegram.bot.token=
                telegram.bot.chatId=

                #UI
                server.port=8080
                server.memory.xmx=8
                server.memory.xms=1
                server.jar=server.jar
                server.stats.poll.interval=3

                #Auth
                security.user.name=admin
                security.user.password=admin

                #Other
                logging.level.org.springframework.web=DEBUG
                """;

                Files.writeString(configPath, defaultConfig);
            }
        } catch (IOException e) {
            System.err.println("Failed to handle config file: " + e.getMessage());
        }
    }
}