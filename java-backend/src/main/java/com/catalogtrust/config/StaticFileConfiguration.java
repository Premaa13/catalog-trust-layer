package com.catalogtrust.config;

import com.catalogtrust.service.ProjectDataPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticFileConfiguration implements WebMvcConfigurer {
    private final ProjectDataPaths paths;

    public StaticFileConfiguration(ProjectDataPaths paths) {
        this.paths = paths;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations(paths.projectFile("dashboard/static").toUri().toString());
        registry.addResourceHandler("/shop/static/**")
                .addResourceLocations(paths.projectFile("shop/static").toUri().toString());
    }
}
