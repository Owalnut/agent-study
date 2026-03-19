package com.walnut.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_definition")
public class WorkflowDefinitionEntity {
    private Long id;
    private String name;
    private String workflowJson;
    private Integer isDraft;
    private Integer isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
