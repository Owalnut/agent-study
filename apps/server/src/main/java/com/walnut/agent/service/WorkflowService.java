package com.walnut.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walnut.agent.dto.WorkflowDtos;
import com.walnut.agent.entity.WorkflowDefinitionEntity;
import com.walnut.agent.entity.WorkflowExecutionEntity;
import com.walnut.agent.mapper.WorkflowDefinitionMapper;
import com.walnut.agent.mapper.WorkflowExecutionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class WorkflowService {
    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final ObjectMapper objectMapper;
    private final int nodeTimeoutMs;
    private final int retryTimes;
    private final long retryBackoffMs;
    private final int retryBackoffMultiplier;

    public WorkflowService(
            WorkflowDefinitionMapper definitionMapper,
            WorkflowExecutionMapper executionMapper,
            ObjectMapper objectMapper,
            @Value("${workflow.node-timeout-ms}") int nodeTimeoutMs,
            @Value("${workflow.retry-times}") int retryTimes,
            @Value("${workflow.retry-backoff-ms}") long retryBackoffMs,
            @Value("${workflow.retry-backoff-multiplier}") int retryBackoffMultiplier
    ) {
        this.definitionMapper = definitionMapper;
        this.executionMapper = executionMapper;
        this.objectMapper = objectMapper;
        this.nodeTimeoutMs = nodeTimeoutMs;
        this.retryTimes = retryTimes;
        this.retryBackoffMs = retryBackoffMs;
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public WorkflowDtos.WorkflowDefinitionResponse saveWorkflow(WorkflowDtos.SaveWorkflowRequest req) {
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setName(req.name());
        entity.setWorkflowJson(writeJson(req.workflow()));
        entity.setIsDraft(req.draft() ? 1 : 0);
        entity.setIsPublished(req.published() ? 1 : 0);
        definitionMapper.insert(entity);
        return new WorkflowDtos.WorkflowDefinitionResponse(entity.getId(), entity.getName(), req.workflow(), req.draft(), req.published());
    }

    public Map<String, Object> getDefaultWorkflow() {
        return Map.of(
                "nodes", List.of(
                        Map.of("id", "input-default", "type", "input", "data", Map.of("name", "输入")),
                        Map.of("id", "output-default", "type", "output", "data", Map.of("name", "输出"))
                ),
                "edges", List.of(
                        Map.of("id", "e1", "source", "input-default", "target", "output-default")
                )
        );
    }

    public WorkflowDtos.DebugWorkflowResponse debugWorkflow(WorkflowDtos.DebugWorkflowRequest req) {
        return debugWorkflowWithEvents(req, null);
    }

    public WorkflowDtos.DebugWorkflowResponse debugWorkflowWithEvents(
            WorkflowDtos.DebugWorkflowRequest req,
            Consumer<DebugEvent> eventConsumer
    ) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflowId(req.workflowId() == null ? 0L : req.workflowId());
        execution.setInputText(req.input());
        execution.setStatus("RUNNING");
        executionMapper.insert(execution);
        emit(eventConsumer, "RUNNING", execution.getId(), Map.of("message", "workflow started"));

        try {
            Map<String, Object> workflowMap = req.workflow() != null ? req.workflow() : getDefaultWorkflow();
            WorkflowDtos.WorkflowGraph graph = objectMapper.convertValue(workflowMap, WorkflowDtos.WorkflowGraph.class);
            WorkflowDtos.DebugOutput output = executeGraph(graph, req.input(), execution.getId(), eventConsumer);
            execution.setStatus("SUCCESS");
            execution.setOutputText(output.text());
            execution.setAudioBase64(output.audioBase64());
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);
            emit(eventConsumer, "SUCCESS", execution.getId(), Map.of("output", output));
            return new WorkflowDtos.DebugWorkflowResponse(true, execution.getId(), "SUCCESS", null, output, null);
        } catch (WorkflowNodeException e) {
            execution.setStatus(e.status());
            execution.setErrorMessage(e.getMessage());
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);
            emit(eventConsumer, "NODE_ERROR", execution.getId(), Map.of("errorCode", e.code(), "message", e.getMessage()));
            return new WorkflowDtos.DebugWorkflowResponse(false, execution.getId(), e.status(), e.code(), null, e.getMessage());
        } catch (Exception e) {
            execution.setStatus("FAILED");
            execution.setErrorMessage(e.getMessage());
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);
            emit(eventConsumer, "FAILED", execution.getId(), Map.of("errorCode", "INTERNAL_ERROR", "message", e.getMessage()));
            return new WorkflowDtos.DebugWorkflowResponse(false, execution.getId(), "FAILED", "INTERNAL_ERROR", null, e.getMessage());
        }
    }

    public WorkflowDtos.WorkflowExecutionResponse getExecution(Long executionId) {
        WorkflowExecutionEntity entity = executionMapper.selectById(executionId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "execution not found");
        }
        return new WorkflowDtos.WorkflowExecutionResponse(
                entity.getId(),
                entity.getWorkflowId(),
                entity.getInputText(),
                entity.getStatus(),
                entity.getOutputText(),
                entity.getAudioBase64(),
                entity.getErrorMessage()
        );
    }

    private WorkflowDtos.DebugOutput executeGraph(
            WorkflowDtos.WorkflowGraph graph,
            String input,
            Long executionId,
            Consumer<DebugEvent> eventConsumer
    ) throws Exception {
        List<WorkflowDtos.Node> sorted = topologicalSort(graph.nodes(), graph.edges());
        Map<String, Map<String, String>> values = new HashMap<>();
        Map<String, String> output = Map.of("text", input);
        for (WorkflowDtos.Node node : sorted) {
            emit(eventConsumer, "NODE_START", executionId, Map.of("nodeId", node.id(), "nodeType", node.type()));
            if ("input".equals(node.type())) {
                values.put(node.id(), Map.of("text", input));
                emit(eventConsumer, "NODE_SUCCESS", executionId, Map.of("nodeId", node.id(), "text", input));
                continue;
            }
            Map<String, String> prev = findIncomingValue(node.id(), graph.edges(), values);
            if ("llm".equals(node.type())) {
                String prompt = prev.getOrDefault("text", input);
                String llmText = executeWithRetry(() -> runWithTimeout(() -> "【LLM模拟】" + prompt));
                values.put(node.id(), Map.of("text", llmText));
                emit(eventConsumer, "NODE_SUCCESS", executionId, Map.of("nodeId", node.id(), "text", llmText));
            } else if ("tool_tts".equals(node.type())) {
                String text = prev.getOrDefault("text", "");
                byte[] wav = executeWithRetry(() -> runWithTimeout(() -> generateSimpleWav()));
                values.put(node.id(), Map.of("text", text, "audioBase64", Base64.getEncoder().encodeToString(wav), "contentType", "audio/wav"));
                emit(eventConsumer, "NODE_SUCCESS", executionId, Map.of("nodeId", node.id(), "audioReady", true));
            } else if ("output".equals(node.type())) {
                output = prev;
                values.put(node.id(), output);
                emit(eventConsumer, "NODE_SUCCESS", executionId, Map.of("nodeId", node.id(), "text", output.get("text")));
            }
        }
        return new WorkflowDtos.DebugOutput(output.get("text"), output.get("audioBase64"), output.getOrDefault("contentType", "audio/wav"));
    }

    private void emit(Consumer<DebugEvent> consumer, String type, Long executionId, Object payload) {
        if (consumer == null) return;
        consumer.accept(new DebugEvent(type, executionId, payload));
    }

    private <T> T executeWithRetry(Callable<T> callable) throws Exception {
        Exception last = null;
        long backoff = retryBackoffMs;
        for (int i = 0; i < retryTimes; i++) {
            try {
                return callable.call();
            } catch (Exception e) {
                last = e;
                if (i < retryTimes - 1 && backoff > 0) {
                    Thread.sleep(backoff);
                    backoff = backoff * Math.max(1, retryBackoffMultiplier);
                }
            }
        }
        throw new WorkflowNodeException("NODE_RETRY_EXHAUSTED", "FAILED", "Node execution failed after retries", last);
    }

    private <T> T runWithTimeout(Callable<T> callable) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(callable);
        try {
            return future.get(nodeTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new WorkflowNodeException("NODE_TIMEOUT", "TIMEOUT", "Node execution timeout", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new WorkflowNodeException("NODE_INTERRUPTED", "INTERRUPTED", "Node execution interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<WorkflowDtos.Node> topologicalSort(List<WorkflowDtos.Node> nodes, List<WorkflowDtos.Edge> edges) {
        Map<String, WorkflowDtos.Node> nodeMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (WorkflowDtos.Node node : nodes) {
            nodeMap.put(node.id(), node);
            inDegree.put(node.id(), 0);
            graph.put(node.id(), new ArrayList<>());
        }
        for (WorkflowDtos.Edge edge : edges) {
            graph.get(edge.source()).add(edge.target());
            inDegree.put(edge.target(), inDegree.get(edge.target()) + 1);
        }
        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) queue.offer(id);
        });
        List<WorkflowDtos.Node> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            result.add(nodeMap.get(id));
            for (String next : graph.get(id)) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) queue.offer(next);
            }
        }
        if (result.size() != nodes.size()) throw new RuntimeException("Workflow has cycle");
        return result;
    }

    private Map<String, String> findIncomingValue(String nodeId, List<WorkflowDtos.Edge> edges, Map<String, Map<String, String>> values) {
        for (WorkflowDtos.Edge edge : edges) {
            if (edge.target().equals(nodeId)) {
                return values.getOrDefault(edge.source(), Map.of());
            }
        }
        return Map.of();
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid workflow json");
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot serialize workflow json");
        }
    }

    private byte[] generateSimpleWav() {
        int sampleRate = 16000;
        int durationMs = 800;
        int totalSamples = sampleRate * durationMs / 1000;
        byte[] pcm = new byte[totalSamples * 2];
        double frequency = 440.0;
        for (int i = 0; i < totalSamples; i++) {
            short value = (short) (Math.sin(2 * Math.PI * frequency * i / sampleRate) * 32767 * 0.2);
            pcm[i * 2] = (byte) (value & 0xff);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
        }
        return wavWrap(pcm, sampleRate, 1, 16);
    }

    private byte[] wavWrap(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + pcm.length);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);
        buffer.put("data".getBytes());
        buffer.putInt(pcm.length);
        buffer.put(pcm);
        return buffer.array();
    }

    private static class WorkflowNodeException extends RuntimeException {
        private final String code;
        private final String status;

        private WorkflowNodeException(String code, String status, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.status = status;
        }

        public String code() {
            return code;
        }

        public String status() {
            return status;
        }
    }

    public record DebugEvent(String type, Long executionId, Object payload) {}
}
