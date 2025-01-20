package com.sam.jarstatusportal.Entity;

import jakarta.websocket.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    // Map session IDs to WebSocket sessions
    private static final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    // Map session IDs to the last Pong received timestamp
    private static final ConcurrentHashMap<String, Instant> lastPongReceived = new ConcurrentHashMap<>();

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LogWebSocketHandler.class);


    @Autowired
    private StringRedisTemplate redisTemplate;


    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        // Extract sessionId parameter from the WebSocket URL
        String sessionId = session.getUri().getQuery().split("=")[1];
        sessionMap.put(sessionId, session);
        lastPongReceived.put(sessionId, Instant.now()); // Initialize with the current timestamp
        logger.info("WebSocket Connection Established for sessionId: {}", sessionId);

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessionMap.values().remove(session);

        // Update Redis to mark the session as DOWN
        String sessionId = session.getUri().getQuery().split("=")[1];
        lastPongReceived.remove(sessionId); // Remove the pong tracking for the session
        redisTemplate.opsForHash().put("sessionStatus", sessionId, "DOWN");

        logger.info("WebSocket Connection Closed: {}", session.getId());
    }

    public void sendLogToClient(String sessionId, String logMessage) {
        WebSocketSession session = sessionMap.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(logMessage));

                // Persist log in Redis
                String redisKey = "logs:" + sessionId;
                redisTemplate.opsForList().rightPush(redisKey, logMessage);

                // Print confirmation of saving to Redis (leave it)
//                System.out.println("Log saved to Redis: " + logMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Scheduled(fixedRate = 30000) // Send a ping every 30 seconds
    public void sendPingMessages() {
        sessionMap.forEach((sessionId, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new PingMessage(ByteBuffer.wrap("ping".getBytes())));


                    logger.info("Ping sent to sessionId: {}", sessionId);
                }
            } catch (IOException e) {
                System.err.println("Failed to send ping to sessionId: " + sessionId);
            }
        });
    }


//    @Scheduled(fixedRate = 30000) // Check every 30 seconds
//    public void checkStaleConnections() {
//        Instant now = Instant.now();
//
//        sessionMap.forEach((sessionId, session) -> {
//            // Assume lastPongReceived is updated whenever a pong is received
//            Instant lastPong = lastPongReceived.getOrDefault(sessionId, Instant.EPOCH);
//
//            if (lastPong.isBefore(now.minusSeconds(60))) { // No pong in the last 60 seconds
//                System.out.println("Stale connection detected for sessionId: " + sessionId);
//                try {
//                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                sessionMap.remove(sessionId);
//                redisTemplate.opsForHash().put("sessionStatus", sessionId, "DOWN");
//            }
//        });
//    }


    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        String sessionId = session.getUri().getQuery().split("=")[1];

        logger.info("Pong received from sessionId: {}", sessionId);

        // Mark the session as UP in Redis
        redisTemplate.opsForHash().put("sessionStatus", sessionId, "UP");

    }


}
