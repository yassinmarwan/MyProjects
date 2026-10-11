package com.team22.eventticketing.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 1. ORIGINAL JSON SERIALIZER (Untouched)
        RedisSerializer<Object> json = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.team22.eventticketing")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.lang")
                        .build())
                .enableSpringCacheNullValueSupport()
                .build();

        // 2. THE BULLETPROOF JDK SERIALIZER (Bypasses JSON entirely)
        RedisSerializer<Object> jdkSerializer = new JdkSerializationRedisSerializer();

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        // --- M1 Features ---
        configs.put("booking-service::S3-F1", cfg(Duration.ofMinutes(5),  json));
        configs.put("booking-service::S3-F3", cfg(Duration.ofMinutes(5),  json));
        configs.put("booking-service::S3-F5", cfg(Duration.ofMinutes(5),  json));
        configs.put("booking-service::S3-F6", cfg(Duration.ofMinutes(10), json));
        configs.put("booking-service::S3-F9", cfg(Duration.ofMinutes(10), json));
        configs.put("booking-service::booking",      cfg(Duration.ofMinutes(15), json));
        configs.put("booking-service::booking-item", cfg(Duration.ofMinutes(15), json));

        // --- M2 Features ---
        configs.put("booking-service::S3-F10", cfg(Duration.ofMinutes(10), jdkSerializer));

        return RedisCacheManager.builder(factory)
                .withInitialCacheConfigurations(configs)
                .cacheDefaults(cfg(Duration.ofMinutes(10), json))
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisSerializer<Object> json = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.team22.eventticketing")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.lang")
                        .build())
                .enableSpringCacheNullValueSupport()
                .build();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(json);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(json);
        return template;
    }

    private RedisCacheConfiguration cfg(Duration ttl, RedisSerializer<Object> json) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(json));
    }
}
