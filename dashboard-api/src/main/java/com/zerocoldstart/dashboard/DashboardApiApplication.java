package com.zerocoldstart.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.zerocoldstart.dashboard.config.KubernetesConfig.KubernetesProperties;
import com.zerocoldstart.dashboard.config.KubernetesConfig.ProjectProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({KubernetesProperties.class, ProjectProperties.class})
public class DashboardApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApiApplication.class, args);
    }
}
