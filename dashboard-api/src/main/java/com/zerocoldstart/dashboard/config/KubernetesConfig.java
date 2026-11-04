package com.zerocoldstart.dashboard.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class KubernetesConfig {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }

    @Data
    @ConfigurationProperties(prefix = "kubernetes")
    public static class KubernetesProperties {
        private String namespace = "library";
        private List<ServiceConfig> services;

        @Data
        public static class ServiceConfig {
            private String name;
            private String jvmImage;
            private String nativeImage;
            private int port;
        }
    }

    @Data
    @ConfigurationProperties(prefix = "projects")
    public static class ProjectProperties {
        private String basePath;
        private String benchmarkScript;
        private String coldstartScript;
        private String resultsPath;
    }
}
