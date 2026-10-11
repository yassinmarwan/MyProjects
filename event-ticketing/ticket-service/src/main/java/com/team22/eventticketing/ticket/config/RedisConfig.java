package com.team22.eventticketing.ticket.config;

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
                configs.put("ticket-service::S4-F1", cfg(Duration.ofMinutes(5), json));
                configs.put("ticket-service::S4-F3", cfg(Duration.ofMinutes(10), json));
                configs.put("ticket-service::S4-F5", cfg(Duration.ofMinutes(5), json));
                configs.put("ticket-service::S4-F6", cfg(Duration.ofMinutes(10), json));
                configs.put("ticket-service::S4-F8", cfg(Duration.ofMinutes(15), json));
                configs.put("ticket-service::S4-F9", cfg(Duration.ofMinutes(10), json));
                configs.put("ticket-service::ticket", cfg(Duration.ofMinutes(15), json));

                // --- M2 Features ---
                // S4-F10 uses JDK Serialization -> ZERO ClassCastExceptions!
                configs.put("ticket-service::S4-F10", cfg(Duration.ofMinutes(10), jdkSerializer));
                // S4-F12 
                configs.put("ticket-service::S4-F12", cfg(Duration.ofMinutes(5), json));


                return RedisCacheManager.builder(factory)
                        .withInitialCacheConfigurations(configs)
                        .cacheDefaults(cfg(Duration.ofMinutes(10), json))
                        .enableStatistics()
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
                        .computePrefixWith(cacheName ->
                                "ticket-service::" + cacheName + "::")
                        .entryTtl(ttl)
                        .serializeKeysWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(json));
        }


}
