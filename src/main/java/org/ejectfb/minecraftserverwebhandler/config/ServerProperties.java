package org.ejectfb.minecraftserverwebhandler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "server")
public class ServerProperties {
    private Memory memory;
    private String jar;
    private int statsPollInterval;
    private int port;
    private boolean autoRun;
    private Telegram telegram = new Telegram();
    private Security security = new Security();

    public static class Memory {
        private int xmx;
        private int xms;

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

    public static class Telegram {
        private String botToken;
        private String chatId;

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getChatId() {
            return chatId;
        }

        public void setChatId(String chatId) {
            this.chatId = chatId;
        }
    }

    public static class Security {
        private String userName;
        private String userPassword;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getUserPassword() {
            return userPassword;
        }

        public void setUserPassword(String userPassword) {
            this.userPassword = userPassword;
        }
    }

    // Геттеры и сеттеры для основных полей
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

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Telegram getTelegram() {
        return telegram;
    }

    public void setTelegram(Telegram telegram) {
        this.telegram = telegram;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public boolean isAutoRun() {
        return autoRun;
    }

    public void setAutoRun(boolean autoRun) {
        this.autoRun = autoRun;
    }
}