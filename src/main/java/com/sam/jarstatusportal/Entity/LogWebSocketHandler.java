package com.sam.jarstatusportal.Entity;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    // Map session IDs to WebSocket sessions
    private static final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Extract sessionId parameter from the WebSocket URL
        String sessionId = session.getUri().getQuery().split("=")[1];
        sessionMap.put(sessionId, session);
        System.out.println("WebSocket Connection Established for sessionId: " + sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessionMap.values().remove(session);
        System.out.println("WebSocket Connection Closed: " + session.getId());
    }

    public void sendLogToClient(String sessionId, String logMessage) {
        WebSocketSession session = sessionMap.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(logMessage));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
