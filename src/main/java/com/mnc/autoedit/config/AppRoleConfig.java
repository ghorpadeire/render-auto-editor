package com.mnc.autoedit.config;

import com.mnc.autoedit.storage.LocalStorageService;
import com.mnc.autoedit.worker.WorkerLoop;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppRoleConfig {

    @Bean
    ApplicationRunner roleRunner(
            ObjectProvider<WorkerLoop> workerLoop,
            ObjectProvider<LocalStorageService> localStorageProvider
    ) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                WorkerLoop loop = workerLoop.getIfAvailable();
                if (loop != null) {
                    boolean localMode = localStorageProvider.getIfAvailable() != null;
                    if (localMode) {
                        loop.forceStart();
                    } else {
                        loop.maybeStart();
                    }
                }
            }
        };
    }
}

