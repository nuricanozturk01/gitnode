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
package com.nuricanozturk.originhub.shared.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Propagates circuit breaker OPEN/CLOSED transitions to Redis so all app instances respect the same
 * open state without waiting for local sliding-window saturation.
 *
 * <p>Key format: {@code cb:open:{cbName}}. TTL = CB wait-duration + 5 s buffer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class DistributedCircuitBreakerGuard {

  static final String KEY_PREFIX = "cb:open:";
  private static final Duration BUFFER = Duration.ofSeconds(5);

  private final StringRedisTemplate redisTemplate;
  private final Set<String> registered = ConcurrentHashMap.newKeySet();

  /**
   * Registers a state-transition listener on {@code cb} (idempotent — safe to call on every
   * request).
   */
  public void registerIfAbsent(final CircuitBreaker cb) {
    if (!this.registered.add(cb.getName())) {
      return;
    }
    final Duration waitDuration =
        Duration.ofMillis(
            cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1));
    cb.getEventPublisher()
        .onStateTransition(
            event -> {
              final CircuitBreaker.State to = event.getStateTransition().getToState();
              final String key = KEY_PREFIX + cb.getName();
              if (to == CircuitBreaker.State.OPEN) {
                this.redisTemplate.opsForValue().set(key, "1", waitDuration.plus(BUFFER));
                log.info(
                    "CB '{}' OPEN — propagated to Redis TTL={}s",
                    cb.getName(),
                    waitDuration.toSeconds());
              } else if (to == CircuitBreaker.State.CLOSED
                  || to == CircuitBreaker.State.HALF_OPEN) {
                this.redisTemplate.delete(key);
                log.info("CB '{}' {} — cleared from Redis", cb.getName(), to);
              }
            });
  }

  /** Returns {@code true} if any instance has opened this circuit breaker. */
  public boolean isGloballyOpen(final String cbName) {
    return Boolean.TRUE.equals(this.redisTemplate.hasKey(KEY_PREFIX + cbName));
  }
}
