package com.walnut.agent.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walnut.agent.dto.WorkflowDtos;
import com.walnut.agent.service.JwtService;
import com.walnut.agent.service.WorkflowService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Component
public class WorkflowDebugWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final WorkflowService workflowService;
    private final JwtService jwtService;

    public WorkflowDebugWebSocketHandler(ObjectMapper objectMapper, WorkflowService workflowService, JwtService jwtService) {
        this.objectMapper = objectMapper;
        this.workflowService = workflowService;
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getTokenFromUri(session.getUri());
        if (token == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        try {
            jwtService.parseSubject(token);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        if (!"START_DEBUG".equals(type)) {
            send(session, "ERROR", null, Map.of("message", "unsupported ws message type"));
            return;
        }

        JsonNode payload = root.path("payload");
        String input = payload.path("input").asText("");
        Long workflowId = payload.hasNonNull("workflowId") ? payload.get("workflowId").asLong() : null;
        JsonNode workflowNode = payload.get("workflow");
        Map<String, Object> workflow = workflowNode == null || workflowNode.isNull()
                ? null
                : objectMapper.convertValue(workflowNode, Map.class);

        WorkflowDtos.DebugWorkflowRequest req = new WorkflowDtos.DebugWorkflowRequest(workflowId, workflow, input);
        WorkflowDtos.DebugWorkflowResponse resp = workflowService.debugWorkflowWithEvents(
                req,
                event -> send(session, event.type(), event.executionId(), event.payload())
        );

        if (!resp.ok()) {
            send(session, "FAILED", resp.executionId(), Map.of(
                    "executionStatus", resp.executionStatus(),
                    "errorCode", resp.errorCode(),
                    "error", resp.error()
            ));
        } else {
            send(session, "COMPLETED", resp.executionId(), Map.of(
                    "executionStatus", resp.executionStatus(),
                    "output", resp.output()
            ));
        }
    }

    private void send(WebSocketSession session, String type, Long executionId, Object payload) {
        try {
            if (!session.isOpen()) return;
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("executionId", executionId);
            message.put("payload", payload);
            String body = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(body));
        } catch (Exception ignored) {
        }
    }

    private String getTokenFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String item : uri.getQuery().split("&")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2 && "token".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
