package com.team22.eventticketing.sales.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
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
        RedisSerializer<Object> json = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.team22.eventticketing")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.lang")
                        .build())
                .enableSpringCacheNullValueSupport()
                .build();

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("sales-service::S5-F1", cfg(Duration.ofMinutes(5),  json));
        configs.put("sales-service::S5-F3", cfg(Duration.ofMinutes(10), json));
        configs.put("sales-service::S5-F6", cfg(Duration.ofMinutes(10), json));
        configs.put("sales-service::S5-F8", cfg(Duration.ofMinutes(15), json));
        configs.put("sales-service::S5-F9",  cfg(Duration.ofMinutes(10), json));
        configs.put("sales-service::S5-F10", cfg(Duration.ofMinutes(10), json));
        configs.put("sales-service::S5-F11", cfg(Duration.ofMinutes(10), json));
        configs.put("sales-service::ticket-sale",    cfg(Duration.ofMinutes(15), json));
        configs.put("sales-service::promotion",      cfg(Duration.ofMinutes(15), json));
        configs.put("sales-service::sale-promotion", cfg(Duration.ofMinutes(15), json));

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
