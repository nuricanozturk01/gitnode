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
package com.nuricanozturk.originhub.shared.ratelimit;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.TooManyRequestsException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class RateLimitService {

  private final StringRedisTemplate stringRedisTemplate;

  public void enforce(final String key, final int limit, final int windowSeconds) {

    final Long count = this.stringRedisTemplate.opsForValue().increment(key);

    if (count == null) {
      log.warn("Redis rate limit increment returned null for key {}, allowing request", key);
      return;
    }

    if (count == 1L) {
      this.stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }

    if (count > limit) {
      log.warn("Rate limit exceeded for key {}: {}/{}", key, count, limit);
      throw new TooManyRequestsException("rateLimitExceeded");
    }
  }
}
