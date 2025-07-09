package org.ejectfb.minecraftserverwebhandler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerStats {
    private final String status;
    private final String timestamp;
    private final String onlinePlayers;
    private final String tps;
    private final String memory;
    private final String upTime;
    private String error;

    public ServerStats(String onlinePlayers,
                       String tps,
                       String memory,
                       String upTime,
                       String timestamp,
                       String status) {
        this.onlinePlayers = onlinePlayers;
        this.tps = tps;
        this.memory = memory;
        this.upTime = upTime;
        this.timestamp = timestamp;
        this.status = status;
    }

    public ServerStats(String error) {
        this(null, null, null, null, null, null);
        this.error = error;
    }

    // Геттеры
    public String getOnlinePlayers() {
        return onlinePlayers;
    }

    public String getTps() {
        return tps;
    }

    public String getMemory() {
        return memory;
    }

    public String getUpTime() {
        return upTime;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String toFormattedString() {
        if (error != null) {
            return "Error: " + error;
        }

        return String.format(
                "=== Server Stats [%s] ===\n" +
                        "Status: %s\n" +
                        "Players Online: %s\n" +
                        "TPS: %s\n" +
                        "Memory Usage: %s\n" +
                        "Uptime: %s",
                timestamp, status, onlinePlayers, tps, memory, upTime
        );
    }

    public static class Builder {
        private String onlinePlayers;
        private String tps;
        private String memory;
        private String upTime;
        private String timestamp;
        private String status;

        public Builder onlinePlayers(String onlinePlayers) {
            this.onlinePlayers = onlinePlayers;
            return this;
        }

        public Builder tps(String tps) {
            this.tps = tps;
            return this;
        }

        public Builder memory(String memory) {
            this.memory = memory;
            return this;
        }

        public Builder upTime(String upTime) {
            this.upTime = upTime;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public ServerStats build() {
            return new ServerStats(
                    onlinePlayers,
                    tps,
                    memory,
                    upTime,
                    timestamp,
                    status
            );
        }
    }
}