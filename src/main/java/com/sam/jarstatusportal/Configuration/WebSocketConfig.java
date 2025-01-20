package com.sam.jarstatusportal.Configuration;


import com.sam.jarstatusportal.Entity.LogWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LogWebSocketHandler logWebSocketHandler;

    // Inject the Spring-managed LogWebSocketHandler bean
    public WebSocketConfig(LogWebSocketHandler logWebSocketHandler) {
        this.logWebSocketHandler = logWebSocketHandler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Use the Spring-managed bean
        registry.addHandler(logWebSocketHandler, "/ws/logs")
                .setAllowedOrigins("*"); // Allow all origins for testing
    }


}
