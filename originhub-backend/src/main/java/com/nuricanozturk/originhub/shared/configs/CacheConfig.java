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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Slf4j
@NullMarked
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  private static final Duration REPO_META_TTL = Duration.ofMinutes(5);
  private static final Duration REPO_PR_OPEN_COUNT_TTL = Duration.ofMinutes(2);
  private static final Duration REPO_ISSUE_OPEN_COUNT_TTL = Duration.ofMinutes(2);
  private static final Duration BRANCH_TTL = Duration.ofMinutes(5);
  private static final Duration TAG_TTL = Duration.ofMinutes(10);
  private static final Duration TREE_TTL = Duration.ofMinutes(10);
  private static final Duration BLOB_TTL = Duration.ofMinutes(10);
  private static final Duration LANGUAGE_TTL = Duration.ofMinutes(10);
  private static final Duration COMMIT_TTL = Duration.ofMinutes(10);
  private static final Duration SNIPPET_DETAIL_TTL = Duration.ofMinutes(10);
  private static final Duration SNIPPET_LIST_PUBLIC_TTL = Duration.ofMinutes(2);
  private static final Duration ADMIN_STATS_TTL = Duration.ofSeconds(300);

  @Bean
  public RedisCacheManager cacheManager(final RedisConnectionFactory connectionFactory) {
    final var ptv =
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.nuricanozturk.originhub")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.lang.")
            .build();

    final var jsonSerializer =
        RedisSerializationContext.SerializationPair.fromSerializer(
            GenericJacksonJsonRedisSerializer.create(
                b -> b.enableDefaultTyping(ptv).typePropertyName("@class")));

    final var defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(jsonSerializer)
            .disableCachingNullValues();

    final Map<String, RedisCacheConfiguration> perRegion =
        Map.ofEntries(
            Map.entry(CacheNames.REPO_META, defaultConfig.entryTtl(REPO_META_TTL)),
            Map.entry(
                CacheNames.REPO_PR_OPEN_COUNT, defaultConfig.entryTtl(REPO_PR_OPEN_COUNT_TTL)),
            Map.entry(
                CacheNames.REPO_ISSUE_OPEN_COUNT,
                defaultConfig.entryTtl(REPO_ISSUE_OPEN_COUNT_TTL)),
            Map.entry(CacheNames.BRANCHES, defaultConfig.entryTtl(BRANCH_TTL)),
            Map.entry(CacheNames.TAGS, defaultConfig.entryTtl(TAG_TTL)),
            Map.entry(CacheNames.TREE, defaultConfig.entryTtl(TREE_TTL)),
            Map.entry(CacheNames.BLOB, defaultConfig.entryTtl(BLOB_TTL)),
            Map.entry(CacheNames.SNIPPET_DETAIL, defaultConfig.entryTtl(SNIPPET_DETAIL_TTL)),
            Map.entry(
                CacheNames.SNIPPET_LIST_PUBLIC, defaultConfig.entryTtl(SNIPPET_LIST_PUBLIC_TTL)),
            Map.entry(CacheNames.LANGUAGES, defaultConfig.entryTtl(LANGUAGE_TTL)),
            Map.entry(CacheNames.COMMITS, defaultConfig.entryTtl(COMMIT_TTL)),
            Map.entry(CacheNames.ADMIN_STATS_OVERVIEW, defaultConfig.entryTtl(ADMIN_STATS_TTL)),
            Map.entry(
                CacheNames.ADMIN_STATS_REPO_ACTIVITY, defaultConfig.entryTtl(ADMIN_STATS_TTL)),
            Map.entry(
                CacheNames.ADMIN_STATS_UPLOAD_ACTIVITY, defaultConfig.entryTtl(ADMIN_STATS_TTL)));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(perRegion)
        .build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(
          final RuntimeException ex, final Cache cache, final Object key) {
        log.debug("Cache GET failed — cache={} key={}: {}", cache.getName(), key, ex.getMessage());
      }

      @Override
      public void handleCachePutError(
          final RuntimeException ex,
          final Cache cache,
          final Object key,
          final @Nullable Object value) {
        log.debug("Cache PUT failed — cache={} key={}: {}", cache.getName(), key, ex.getMessage());
      }

      @Override
      public void handleCacheEvictError(
          final RuntimeException ex, final Cache cache, final Object key) {
        log.debug(
            "Cache EVICT failed — cache={} key={}: {}", cache.getName(), key, ex.getMessage());
      }

      @Override
      public void handleCacheClearError(final RuntimeException ex, final Cache cache) {
        log.debug("Cache CLEAR failed — cache={}: {}", cache.getName(), ex.getMessage());
      }
    };
  }
}
