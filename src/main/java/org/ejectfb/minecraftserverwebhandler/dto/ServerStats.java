package org.ejectfb.minecraftserverwebhandler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)

public record ServerStats(String onlinePlayers, String tps, String memory, String upTime, String timestamp,
                          String status) {
}