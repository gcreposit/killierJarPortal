package com.sam.jarstatusportal.Entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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




    @Autowired
    private StringRedisTemplate redisTemplate;


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

                // Persist log in Redis
                String redisKey = "logs:" + sessionId;
                redisTemplate.opsForList().rightPush(redisKey, logMessage);

                // Print confirmation of saving to Redis
                System.out.println("Log saved to Redis: " + logMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }




}
