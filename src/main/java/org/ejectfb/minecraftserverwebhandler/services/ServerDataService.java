package org.ejectfb.minecraftserverwebhandler.services;

import jakarta.annotation.PostConstruct;
import org.ejectfb.minecraftserverwebhandler.dto.ServerStats;
import org.ejectfb.minecraftserverwebhandler.utils.StringUtils;
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

    @PostConstruct
    public void init() {
        reset();
    }

    public void parseConsoleLine(String line) {
        if (line.contains("There are ") && line.contains(" players online")) {
            onlinePlayers.set(parseOnlinePlayers(line));
            parseOnlinePlayers(line);
        } else if (line.contains("Current Memory Usage:")) {
            memory.set(parseMemory(line));
            parseMemory(line);
        } else if (line.contains("TPS from last")) {
            tps.set(parseTPS(line));
        }
        uptime.set(calculateUptime());
    }

    public String parseOnlinePlayers(String consoleText) {
        String[] lines = consoleText.split("\n");
        String matchPhrase = "[Server thread/INFO]: There are ";

        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].contains(matchPhrase)) {
                String result = lines[i].substring(lines[i].indexOf(matchPhrase) + matchPhrase.length());
                return result.substring(0, result.indexOf(" "));
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
                return memoryUsage.trim();
            }
        }
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
                    return tpsValues[0].trim();
                }
            }
        }
        tps.set("N/A");
        return "N/A";
    }

    public String calculateUptime() {
        if (serverStartTime.get() == 0) {
            return "N/A";
        }

        long uptimeMillis = System.currentTimeMillis() - serverStartTime.get();
        return formatDuration(uptimeMillis);
    }

    private String formatDuration(long millis) {
        return StringUtils.formatDuration(millis);
    }

    public void setServerStartTime(long startTime) {
        this.serverStartTime.set(startTime);
        uptime.set(calculateUptime());
    }

    public void reset() {
        onlinePlayers.set("N/A");
        tps.set("N/A");
        memory.set("N/A");
        uptime.set("N/A");
        serverStartTime.set(0);
    }

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
}