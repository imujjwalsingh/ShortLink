package com.shortlink.analyticsservice.config;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    /**
     * Spring Boot's default JsonpMapper is a bare JacksonJsonpMapper that
     * doesn't know java.time types, so Instant fields would fail to
     * serialize. This one starts from Boot's ObjectMapper (JavaTimeModule,
     * ISO-8601 dates). It gets a copy because JacksonJsonpMapper
     * reconfigures the mapper it's given, and that must not leak into the
     * ObjectMapper used for HTTP responses and Kafka payloads.
     */
    @Bean
    JsonpMapper jsonpMapper(ObjectMapper objectMapper) {
        return new JacksonJsonpMapper(objectMapper.copy());
    }
}
