package com.walnut.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_execution")
public class WorkflowExecutionEntity {
    private Long id;
    private Long workflowId;
    private String inputText;
    private String status;
    private String outputText;
    private String audioBase64;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
