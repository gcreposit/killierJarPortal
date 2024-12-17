package com.sam.jarstatusportal.Entity;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JarWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
    }

    public void sendLogToClients(String logMessage) {
        System.out.println("Sending Log to WebSocket: " + logMessage); // Debug

        sessions.values().forEach(session -> {
            try {
                session.sendMessage(new TextMessage(logMessage));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
