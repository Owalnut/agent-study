package com.walnut.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.walnut.agent.entity.WorkflowDefinitionEntity;
import com.walnut.agent.mapper.WorkflowDefinitionMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initDefaultWorkflow(WorkflowDefinitionMapper mapper) {
        return args -> {
            Long count = mapper.selectCount(new LambdaQueryWrapper<>());
            if (count != null && count > 0) {
                return;
            }
            WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
            entity.setName("default");
            entity.setWorkflowJson("""
                    {
                      "nodes": [
                        {"id": "input-default", "type": "input", "data": {"name": "输入"}},
                        {"id": "output-default", "type": "output", "data": {"name": "输出"}}
                      ],
                      "edges": [
                        {"id": "e1", "source": "input-default", "target": "output-default"}
                      ]
                    }
                    """);
            entity.setIsDraft(0);
            entity.setIsPublished(1);
            mapper.insert(entity);
        };
    }
}
