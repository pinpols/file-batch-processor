package com.example.filebatchprocessor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** 统一提供 RestClient.Builder，避免各告警渠道自行创建不可替换的客户端实例。 */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
