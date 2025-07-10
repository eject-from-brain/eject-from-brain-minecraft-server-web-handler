package org.ejectfb.minecraftserverwebhandler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "server")
public class ServerProperties {
    private Memory memory;
    private String jar;
    private int statsPollInterval;

    // Геттеры и сеттеры
    public static class Memory {
        private int xmx;
        private int xms;

        // Геттеры и сеттеры
        public int getXmx() {
            return xmx;
        }

        public void setXmx(int xmx) {
            this.xmx = xmx;
        }

        public int getXms() {
            return xms;
        }

        public void setXms(int xms) {
            this.xms = xms;
        }
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public String getJar() {
        return jar;
    }

    public void setJar(String jar) {
        this.jar = jar;
    }

    public int getStatsPollInterval() {
        return statsPollInterval;
    }

    public void setStatsPollInterval(int statsPollInterval) {
        this.statsPollInterval = statsPollInterval;
    }
}