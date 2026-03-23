package com.walnut.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;

@SpringBootApplication(exclude = {OpenAiAutoConfiguration.class})
@MapperScan("com.walnut.agent.mapper")
public class AgentServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentServerApplication.class, args);
    }
}
