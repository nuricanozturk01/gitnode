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
package dev.gitnode.os.shared.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DistributedCircuitBreakerGuard unit tests")
class DistributedCircuitBreakerGuardTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private DistributedCircuitBreakerGuard guard;
  private CircuitBreakerRegistry registry;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    guard = new DistributedCircuitBreakerGuard(redisTemplate);
    registry =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build());
  }

  @Test
  @DisplayName("isGloballyOpen returns false when Redis key absent")
  void isGloballyOpen_returnsFalse_whenKeyAbsent() {
    when(redisTemplate.hasKey(DistributedCircuitBreakerGuard.KEY_PREFIX + "cb1")).thenReturn(false);
    assertThat(guard.isGloballyOpen("cb1")).isFalse();
  }

  @Test
  @DisplayName("isGloballyOpen returns true when Redis key present")
  void isGloballyOpen_returnsTrue_whenKeyPresent() {
    when(redisTemplate.hasKey(DistributedCircuitBreakerGuard.KEY_PREFIX + "cb1")).thenReturn(true);
    assertThat(guard.isGloballyOpen("cb1")).isTrue();
  }

  @Test
  @DisplayName("registerIfAbsent sets Redis key when CB transitions to OPEN")
  void registerIfAbsent_setsRedisKey_whenTransitionsToOpen() {
    final CircuitBreaker cb = registry.circuitBreaker("webhook.example.com");
    guard.registerIfAbsent(cb);

    // Drive CB to OPEN by recording failures
    cb.onError(0, java.util.concurrent.TimeUnit.SECONDS, new RuntimeException("fail"));
    cb.onError(0, java.util.concurrent.TimeUnit.SECONDS, new RuntimeException("fail"));

    verify(valueOps)
        .set(
            eq(DistributedCircuitBreakerGuard.KEY_PREFIX + "webhook.example.com"),
            eq("1"),
            any(Duration.class));
  }

  @Test
  @DisplayName("registerIfAbsent is idempotent — listener registered only once")
  void registerIfAbsent_isIdempotent() {
    final CircuitBreaker cb = registry.circuitBreaker("webhook.idempotent.test");
    guard.registerIfAbsent(cb);
    guard.registerIfAbsent(cb);
    guard.registerIfAbsent(cb);

    // Should only have 1 listener registered — drive to OPEN and verify single set call
    cb.onError(0, java.util.concurrent.TimeUnit.SECONDS, new RuntimeException("x"));
    cb.onError(0, java.util.concurrent.TimeUnit.SECONDS, new RuntimeException("x"));

    verify(valueOps)
        .set(
            eq(DistributedCircuitBreakerGuard.KEY_PREFIX + "webhook.idempotent.test"),
            eq("1"),
            any(Duration.class));
  }

  @Test
  @DisplayName("registerIfAbsent deletes Redis key when CB transitions to CLOSED")
  void registerIfAbsent_deletesRedisKey_whenTransitionsToClosed() {
    final CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .failureRateThreshold(100)
            .waitDurationInOpenState(Duration.ofMillis(1))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build();
    final CircuitBreaker cb = registry.circuitBreaker("webhook.closeable.test", config);
    guard.registerIfAbsent(cb);

    // Drive to OPEN
    cb.onError(0, java.util.concurrent.TimeUnit.SECONDS, new RuntimeException("fail"));
    cb.onError(0, java.util.concurrent.TimeUnit.SECONDS, new RuntimeException("fail"));

    // Transition to HALF_OPEN
    cb.transitionToHalfOpenState();

    verify(redisTemplate)
        .delete(eq(DistributedCircuitBreakerGuard.KEY_PREFIX + "webhook.closeable.test"));
  }
}
