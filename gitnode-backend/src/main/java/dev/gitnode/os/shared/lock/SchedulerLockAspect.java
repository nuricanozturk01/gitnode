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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Enforces {@link SchedulerLock}; ordered before transaction advice so the lock wraps the tx. */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchedulerLockAspect {

  private static final String KEY_PREFIX = "lock:sched:";

  private final DistributedLockService lockService;

  @Around("@annotation(dev.gitnode.os.shared.lock.SchedulerLock)")
  public Object around(final ProceedingJoinPoint pjp) throws Throwable {
    final var method = ((MethodSignature) pjp.getSignature()).getMethod();
    final var specific = AopUtils.getMostSpecificMethod(method, pjp.getTarget().getClass());
    final var lock = AnnotationUtils.findAnnotation(specific, SchedulerLock.class);
    if (lock == null) {
      return pjp.proceed();
    }
    return this.run(pjp, lock);
  }

  @SuppressWarnings("checkstyle:IllegalCatch") // lock backend outage is handled by failOpen
  Object run(final ProceedingJoinPoint pjp, final SchedulerLock lock) throws Throwable {
    final String key = KEY_PREFIX + lock.name();
    final String owner = this.lockService.generateOwner();
    final boolean acquired;
    try {
      acquired = this.lockService.tryLock(key, owner, Duration.ofSeconds(lock.ttlSeconds()));
    } catch (final RuntimeException ex) {
      if (lock.failOpen()) {
        log.warn(
            "Lock backend unavailable for {}, running anyway: {}", lock.name(), ex.getMessage());
        return pjp.proceed();
      }
      log.warn("Lock backend unavailable for {}, skipping run: {}", lock.name(), ex.getMessage());
      return null;
    }
    if (!acquired) {
      log.debug("Scheduled job {} skipped: another instance holds the lock", lock.name());
      return null;
    }
    try {
      return pjp.proceed();
    } finally {
      this.lockService.unlock(key, owner);
    }
  }
}
