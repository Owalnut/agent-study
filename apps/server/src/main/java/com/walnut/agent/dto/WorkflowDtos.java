package com.walnut.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class WorkflowDtos {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, long expiresAtEpochMs) {}

    public record SaveWorkflowRequest(
            @NotBlank String name,
            @NotNull Map<String, Object> workflow,
            boolean draft,
            boolean published
    ) {}

    public record WorkflowDefinitionResponse(
            Long id,
            String name,
            Map<String, Object> workflow,
            boolean draft,
            boolean published
    ) {}

    public record DebugWorkflowRequest(
            Long workflowId,
            Map<String, Object> workflow,
            @NotBlank String input
    ) {}

    public record NodeData(
            String name,
            String model,
            String voice,
            String provider,
            String baseUrl,
            String apiKey,
            Double temperature,
            String promptTemplate,
            String inputSourceNodeId,
            List<NodeInputParam> inputParams,
            List<NodeOutputParam> outputParams
    ) {}
    public record NodeInputParam(String name, String type, String value) {}
    public record NodeOutputParam(String name, String valueType, String description) {}
    public record Node(String id, String type, NodeData data) {}
    public record Edge(String id, String source, String target) {}
    public record WorkflowGraph(List<Node> nodes, List<Edge> edges) {}

    public record DebugOutput(String text, String audioBase64, String contentType) {}
    public record DebugWorkflowResponse(
            boolean ok,
            Long executionId,
            String executionStatus,
            String errorCode,
            DebugOutput output,
            String error
    ) {}

    public record WorkflowExecutionResponse(
            Long id,
            Long workflowId,
            String inputText,
            String status,
            String outputText,
            String audioBase64,
            String errorMessage,
            List<NodeResult> nodeResults
    ) {}

    public record NodeResult(
            String nodeId,
            String nodeType,
            String status,
            Long durationMs,
            String text,
            String errorCode
    ) {}
}
