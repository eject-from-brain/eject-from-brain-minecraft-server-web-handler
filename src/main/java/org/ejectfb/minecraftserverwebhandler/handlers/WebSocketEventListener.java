package org.ejectfb.minecraftserverwebhandler.handlers;

import org.ejectfb.minecraftserverwebhandler.services.ConsoleLogService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.context.event.EventListener;

@Component
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConsoleLogService consoleLogService;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate,
                                  ConsoleLogService consoleLogService) {
        this.messagingTemplate = messagingTemplate;
        this.consoleLogService = consoleLogService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        consoleLogService.getLogs().forEach(log ->
                messagingTemplate.convertAndSend("/topic/console", log));
    }
}