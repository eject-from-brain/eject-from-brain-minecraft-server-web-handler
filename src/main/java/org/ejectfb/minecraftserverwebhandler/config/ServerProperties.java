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
    private Backup backup = new Backup();


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
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Backup {
        private boolean enabled;
        private String directory;
        private String backupTime;
        private boolean dailyEnabled;
        private int dailyMaxBackups;
        private boolean weeklyEnabled;
        private int weeklyMaxBackups;
        private boolean monthlyEnabled;
        private int monthlyMaxBackups;
        private boolean enableRestartNotifications;
        private String notificationTemplate;
        private String notificationTimes;

        public boolean isDailyEnabled() { return dailyEnabled; }
        public void setDailyEnabled(boolean dailyEnabled) { this.dailyEnabled = dailyEnabled; }
        public int getDailyMaxBackups() { return dailyMaxBackups; }
        public void setDailyMaxBackups(int dailyMaxBackups) { this.dailyMaxBackups = dailyMaxBackups; }
        public boolean isWeeklyEnabled() { return weeklyEnabled; }
        public void setWeeklyEnabled(boolean weeklyEnabled) { this.weeklyEnabled = weeklyEnabled; }
        public int getWeeklyMaxBackups() { return weeklyMaxBackups; }
        public void setWeeklyMaxBackups(int weeklyMaxBackups) { this.weeklyMaxBackups = weeklyMaxBackups; }
        public boolean isMonthlyEnabled() { return monthlyEnabled; }
        public void setMonthlyEnabled(boolean monthlyEnabled) { this.monthlyEnabled = monthlyEnabled; }
        public int getMonthlyMaxBackups() { return monthlyMaxBackups; }
        public void setMonthlyMaxBackups(int monthlyMaxBackups) { this.monthlyMaxBackups = monthlyMaxBackups; }
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public String getBackupTime() { return backupTime; }
        public void setBackupTime(String backupTime) { this.backupTime = backupTime; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isEnableRestartNotifications() {return enableRestartNotifications;}
        public void setEnableRestartNotifications(boolean enableRestartNotifications) {this.enableRestartNotifications = enableRestartNotifications;}
        public String getNotificationTemplate() {return notificationTemplate;}
        public void setNotificationTemplate(String notificationTemplate) {this.notificationTemplate = notificationTemplate;}
        public String getNotificationTimes() {return notificationTimes;}
        public void setNotificationTimes(String notificationTimes) {this.notificationTimes = notificationTimes;}
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

    public Backup getBackup() {
        return backup;
    }

    public void setBackup(Backup backup) {
        this.backup = backup;
    }
}