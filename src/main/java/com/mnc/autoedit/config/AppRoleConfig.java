package com.mnc.autoedit.config;

import com.mnc.autoedit.worker.WorkerLoop;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppRoleConfig {

    @Bean
    ApplicationRunner roleRunner(ObjectProvider<WorkerLoop> workerLoop) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                WorkerLoop loop = workerLoop.getIfAvailable();
                if (loop != null) {
                    loop.maybeStart();
                }
            }
        };
    }
}

