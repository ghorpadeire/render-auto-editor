package com.mnc.autoedit.tools;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ToolConfig.class)
public class ToolsConfig {
    @Bean
    ProcessRunner processRunner() {
        return new ProcessRunner();
    }
}

