/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nuricanozturk.originhub.shared.configs;

import com.nuricanozturk.originhub.shared.cache.CacheNames;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
public class CacheConfig {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
  private static final Duration BRANCH_TTL = Duration.ofMinutes(5);
  private static final Duration COMMIT_TTL = Duration.ofMinutes(10);

  @Bean
  public RedisCacheManager cacheManager(
      final RedisConnectionFactory connectionFactory, final ObjectMapper objectMapper) {
    final var jsonSerializer =
        RedisSerializationContext.SerializationPair.fromSerializer(
            new GenericJacksonJsonRedisSerializer(objectMapper));

    final var defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(DEFAULT_TTL)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(jsonSerializer)
            .disableCachingNullValues();

    final Map<String, RedisCacheConfiguration> perRegion =
        Map.of(
            CacheNames.BRANCHES, defaultConfig.entryTtl(BRANCH_TTL),
            CacheNames.COMMITS, defaultConfig.entryTtl(COMMIT_TTL));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(perRegion)
        .build();
  }
}
