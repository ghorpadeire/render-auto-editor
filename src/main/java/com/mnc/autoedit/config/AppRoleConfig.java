package com.mnc.autoedit.config;

import com.mnc.autoedit.worker.WorkerLoop;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppRoleConfig {

    @Bean
    ApplicationRunner roleRunner(WorkerLoop workerLoop) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                workerLoop.maybeStart();
            }
        };
    }
}

