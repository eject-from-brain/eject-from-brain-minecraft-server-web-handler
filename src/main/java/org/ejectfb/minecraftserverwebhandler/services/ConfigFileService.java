package org.ejectfb.minecraftserverwebhandler.services;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class ConfigFileService {
    private final ServerProperties serverProperties;

    public ConfigFileService(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    public void saveConfigurationToFile() throws IOException {
        Path configPath = Paths.get("./application.properties");
        Path backupPath = Paths.get("./application.properties.bak");

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

    private String buildConfigContent() {
        return generateConfigContent(
                serverProperties.getTelegram().getBotToken(),
                serverProperties.getTelegram().getChatId(),
                serverProperties.getPort(),
                serverProperties.getMemory().getXmx(),
                serverProperties.getMemory().getXms(),
                serverProperties.getJar(),
                serverProperties.getStatsPollInterval(),
                serverProperties.getSecurity().getUsername(),
                serverProperties.getSecurity().getPassword()
        );
    }

    public static String generateConfigContent(String botToken,
                                               String chatId,
                                               int port,
                                               int memoryXmx,
                                               int memoryXms,
                                               String jar,
                                               int statsPollInterval,
                                               String securityUserName,
                                               String securityUserPassword) {
        return String.format("""
            # Application
            spring.application.name=minecraft-server-web-handler
            
            # Telegram
            telegram.bot.token=%s
            telegram.bot.chatId=%s
            
            # Server
            server.port=%d
            server.memory.xmx=%d
            server.memory.xms=%d
            server.jar=%s
            server.stats-poll-interval=%d
            server.auto-run=false
            
            # Security
            security.user.username=%s
            security.user.password=%s
            
            # Logging
            logging.level.org.springframework.web=DEBUG
            """,
                botToken
                , chatId
                , port
                , memoryXmx
                , memoryXms
                , jar
                , statsPollInterval
                , securityUserName
                , securityUserPassword
        );
    }
}