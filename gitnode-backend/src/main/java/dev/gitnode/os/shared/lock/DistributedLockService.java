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
package dev.gitnode.os.shared.lock;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class DistributedLockService {

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

  static {
    RELEASE_SCRIPT = new DefaultRedisScript<>();
    RELEASE_SCRIPT.setScriptText(
        "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end");
    RELEASE_SCRIPT.setResultType(Long.class);
  }

  private final StringRedisTemplate redisTemplate;

  public boolean tryLock(final String key, final String owner, final Duration ttl) {
    final Boolean acquired = this.redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
    return Boolean.TRUE.equals(acquired);
  }

  public void unlock(final String key, final String owner) {
    try {
      this.redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), owner);
    } catch (final Exception ex) {
      log.warn("Failed to release distributed lock key={}: {}", key, ex.getMessage());
    }
  }

  public String generateOwner() {
    return UUID.randomUUID().toString();
  }
}
