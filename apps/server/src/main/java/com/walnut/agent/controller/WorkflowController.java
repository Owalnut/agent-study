package com.walnut.agent.controller;

import com.walnut.agent.dto.WorkflowDtos;
import com.walnut.agent.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/default")
    public Map<String, Object> getDefaultWorkflow() {
        return workflowService.getDefaultWorkflow();
    }

    @PostMapping
    public WorkflowDtos.WorkflowDefinitionResponse saveWorkflow(@Valid @RequestBody WorkflowDtos.SaveWorkflowRequest req) {
        return workflowService.saveWorkflow(req);
    }

    @PostMapping("/debug")
    public WorkflowDtos.DebugWorkflowResponse debug(@Valid @RequestBody WorkflowDtos.DebugWorkflowRequest req) {
        return workflowService.debugWorkflow(req);
    }

    @GetMapping("/executions/{executionId}")
    public WorkflowDtos.WorkflowExecutionResponse getExecution(@PathVariable Long executionId) {
        return workflowService.getExecution(executionId);
    }
}
