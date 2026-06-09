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
package dev.gitnode.os.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.errorhandling.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService unit tests")
class RateLimitServiceTest {

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  @InjectMocks private RateLimitService rateLimitService;

  @Test
  @DisplayName("enforce allows request when count is within limit")
  void enforce_allowsRequest_whenWithinLimit() {

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(1L);

    assertThatCode(() -> rateLimitService.enforce("test:key", 10, 60)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("enforce sets TTL on first request")
  void enforce_setsTtl_onFirstRequest() {

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(1L);

    rateLimitService.enforce("test:key", 10, 60);

    verify(stringRedisTemplate).expire(anyString(), any());
  }

  @Test
  @DisplayName("enforce does not reset TTL on subsequent requests")
  void enforce_doesNotResetTtl_onSubsequentRequests() {

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(5L);

    rateLimitService.enforce("test:key", 10, 60);

    verify(stringRedisTemplate, org.mockito.Mockito.never()).expire(anyString(), any());
  }

  @Test
  @DisplayName("enforce throws TooManyRequestsException when limit is exceeded")
  void enforce_throws_whenLimitExceeded() {

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(11L);

    assertThatThrownBy(() -> rateLimitService.enforce("test:key", 10, 60))
        .isInstanceOf(TooManyRequestsException.class)
        .hasMessageContaining("rateLimitExceeded");
  }

  @Test
  @DisplayName("enforce allows request exactly at limit boundary")
  void enforce_allowsRequest_atLimitBoundary() {

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(10L);

    assertThatCode(() -> rateLimitService.enforce("test:key", 10, 60)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("enforce allows when Redis returns null (fail open)")
  void enforce_allowsRequest_whenRedisReturnsNull() {

    when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(null);

    assertThatCode(() -> rateLimitService.enforce("test:key", 10, 60)).doesNotThrowAnyException();
  }
}
