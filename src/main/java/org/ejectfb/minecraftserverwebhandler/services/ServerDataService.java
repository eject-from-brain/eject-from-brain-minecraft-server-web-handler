package org.ejectfb.minecraftserverwebhandler.services;

import jakarta.annotation.PostConstruct;
import org.ejectfb.minecraftserverwebhandler.dto.ServerStats;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ServerDataService {

    private final AtomicReference<String> onlinePlayers = new AtomicReference<>("N/A");
    private final AtomicReference<String> tps = new AtomicReference<>("N/A");
    private final AtomicReference<String> memory = new AtomicReference<>("N/A");
    private final AtomicLong serverStartTime = new AtomicLong(0);
    private final AtomicReference<String> uptime = new AtomicReference<>("N/A");
    private final AtomicReference<String> lastConsoleOutput = new AtomicReference<>("");

    @PostConstruct
    public void init() {
        reset();
    }

    public void collectStatsData(String consoleText) {
        lastConsoleOutput.set(consoleText);
        parseOnlinePlayers(consoleText);
        parseTPS(consoleText);
        parseMemory(consoleText);
        updateUptime();
    }

    public ServerStats getStats() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = dtf.format(LocalDateTime.now());
        String status = serverStartTime.get() > 0 ? "Running" : "Stopped";

        return new ServerStats(
                onlinePlayers.get(),
                tps.get(),
                memory.get(),
                uptime.get(),
                timestamp,
                status
        );
    }

    public void parseConsoleLine(String line) {
        if (line.contains("There are ") && line.contains(" players online:")) {
            parseOnlinePlayers(line);
        } else if (line.contains("Current Memory Usage:")) {
            parseMemory(line);
        } else if (line.contains("TPS from last")) {
            parseTPS(line);
        }
        updateUptime();
    }

    public String parseOnlinePlayers(String consoleText) {
        String[] lines = consoleText.split("\n");
        String matchPhrase = "[Server thread/INFO]: There are ";

        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].contains(matchPhrase)) {
                String result = lines[i].substring(lines[i].indexOf(matchPhrase) + matchPhrase.length());
                String players = result.substring(0, result.indexOf(" "));
                onlinePlayers.set(players);
                return players;
            }
        }
        onlinePlayers.set("N/A");
        return "N/A";
    }

    public String parseMemory(String consoleText) {
        String[] lines = consoleText.split("\n");
        String matchPhrase = "Current Memory Usage: ";

        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].contains(matchPhrase)) {
                String memoryUsage = lines[i].substring(lines[i].indexOf(matchPhrase) + matchPhrase.length());
                memory.set(memoryUsage.trim());
                return memoryUsage.trim();
            }
        }
        memory.set("N/A");
        return "N/A";
    }

    public String parseTPS(String consoleText) {
        String[] lines = consoleText.split("\n");

        for (String line : lines) {
            if (line.contains("TPS from last")) {
                String tpsPart = line.substring(line.lastIndexOf(":") + 1).trim();
                tpsPart = tpsPart.replaceAll("[^0-9.,-]", "");
                String[] tpsValues = tpsPart.split(",");
                if (tpsValues.length >= 3) {
                    String firstTps = tpsValues[0].trim();
                    tps.set(firstTps);
                    return firstTps;
                }
            }
        }
        tps.set("N/A");
        return "N/A";
    }

    public String calculateUptime() {
        if (serverStartTime.get() == 0) {
            uptime.set("N/A");
            return "N/A";
        }

        long uptimeMillis = System.currentTimeMillis() - serverStartTime.get();
        String formatted = formatDuration(uptimeMillis);
        uptime.set(formatted);
        return formatted;
    }

    private void updateUptime() {
        calculateUptime();
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        if (days > 0) {
            return String.format("%dд %dч %dм %dс", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dч %dм %dс", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds);
        } else {
            return String.format("%dс", seconds);
        }
    }

    public void setServerStartTime(long startTime) {
        this.serverStartTime.set(startTime);
        updateUptime();
    }

    public void reset() {
        onlinePlayers.set("N/A");
        tps.set("N/A");
        memory.set("N/A");
        uptime.set("N/A");
        serverStartTime.set(0);
        lastConsoleOutput.set("");
    }

    public String getFormattedStats() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format(
                "📊 Статистика сервера Minecraft (%s)\n" +
                        "🔄 Состояние: %s\n" +
                        "🧮 Память: %s\n" +
                        "👥 Онлайн: %s\n" +
                        "⏱ TPS: %s\n" +
                        "⏳ Время работы: %s",
                dtf.format(LocalDateTime.now()),
                serverStartTime.get() > 0 ? "работает" : "остановлен",
                memory.get(),
                onlinePlayers.get(),
                tps.get(),
                uptime.get()
        );
    }

    // Геттеры
    public String getOnlinePlayers() {
        return onlinePlayers.get();
    }

    public String getTps() {
        return tps.get();
    }

    public String getMemory() {
        return memory.get();
    }

    public String getUpTime() {
        return uptime.get();
    }

    public long getServerStartTime() {
        return serverStartTime.get();
    }

    public String getLastConsoleOutput() {
        return lastConsoleOutput.get();
    }
}