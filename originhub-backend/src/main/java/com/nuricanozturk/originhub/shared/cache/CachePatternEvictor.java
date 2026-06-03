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
package com.nuricanozturk.originhub.shared.cache;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class CachePatternEvictor {

  private final StringRedisTemplate redisTemplate;

  public void evict(final String region, final String keyPattern) {
    try {
      final var redisPattern = region + "::" + keyPattern;
      final Set<String> keys = this.redisTemplate.keys(redisPattern);
      if (keys != null && !keys.isEmpty()) {
        this.redisTemplate.delete(keys);
        log.debug("Evicted {} key(s) matching pattern: {}", keys.size(), redisPattern);
      }
    } catch (final Exception ex) {
      log.warn("Cache eviction failed for region={} pattern={}", region, keyPattern, ex);
    }
  }
}
