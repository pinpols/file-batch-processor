package com.example.filebatchprocessor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fileBatchProcessorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("File Batch Processor API")
                        .version("v1")
                        .description("Operational and batch-processing APIs for the monolith file batch processor."));
    }
}
