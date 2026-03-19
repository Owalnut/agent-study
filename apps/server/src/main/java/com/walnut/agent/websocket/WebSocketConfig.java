package com.walnut.agent.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final WorkflowDebugWebSocketHandler workflowDebugWebSocketHandler;

    public WebSocketConfig(WorkflowDebugWebSocketHandler workflowDebugWebSocketHandler) {
        this.workflowDebugWebSocketHandler = workflowDebugWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(workflowDebugWebSocketHandler, "/ws/debug").setAllowedOrigins("http://localhost:5173", "http://localhost:5174");
    }
}
