package com.cardio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String projectPath = new File(".").getAbsolutePath();
        if (projectPath.endsWith(".")) {
            projectPath = projectPath.substring(0, projectPath.length() - 1);
        }
        // Normalize slash direction
        projectPath = projectPath.replace("\\", "/");
        if (!projectPath.endsWith("/")) {
            projectPath += "/";
        }
        String uploadsPath = "file:" + projectPath + "uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsPath);
    }
}
