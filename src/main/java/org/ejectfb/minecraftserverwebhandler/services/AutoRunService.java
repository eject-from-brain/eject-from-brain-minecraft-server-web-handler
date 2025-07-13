package org.ejectfb.minecraftserverwebhandler.services;

import org.ejectfb.minecraftserverwebhandler.config.ServerProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class AutoRunService implements ApplicationRunner {

    private final ServerProperties serverProperties;
    private final ServerService serverService;

    public AutoRunService(ServerProperties serverProperties, ServerService serverService) {
        this.serverProperties = serverProperties;
        this.serverService = serverService;
    }

    @Override
    public void run(ApplicationArguments args) {
    }

    public void autoStartIfEnabled() {
        if (serverProperties.isAutoRun() && !serverService.isServerRunning()) {
            try {
                String command = String.format("java -Xmx%dG -Xms%dG -jar %s nogui",
                        serverProperties.getMemory().getXmx(),
                        serverProperties.getMemory().getXms(),
                        serverProperties.getJar());
                serverService.startServer(command);
                System.out.println("Auto-started server with command: " + command);
            } catch (Exception e) {
                System.err.println("Failed to auto-start server: " + e.getMessage());
            }
        }
    }
}