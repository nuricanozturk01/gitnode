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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerLockAspectTest {

  private DistributedLockService locks;
  private SchedulerLockAspect aspect;
  private ProceedingJoinPoint pjp;

  @SchedulerLock(name = "x", ttlSeconds = 5)
  void closed() {}

  @SchedulerLock(name = "y", ttlSeconds = 5, failOpen = true)
  void open() {}

  private SchedulerLock ann(final String method) throws Exception {
    return SchedulerLockAspectTest.class
        .getDeclaredMethod(method)
        .getAnnotation(SchedulerLock.class);
  }

  @BeforeEach
  void setUp() throws Throwable {
    this.locks = mock(DistributedLockService.class);
    this.pjp = mock(ProceedingJoinPoint.class);
    when(this.pjp.proceed()).thenReturn("ran");
    when(this.locks.generateOwner()).thenReturn("o");
    this.aspect = new SchedulerLockAspect(this.locks);
  }

  @Test
  void runsAndReleasesWhenLockAcquired() throws Throwable {
    when(this.locks.tryLock(eq("lock:sched:x"), eq("o"), any(Duration.class))).thenReturn(true);
    assertEquals("ran", this.aspect.run(this.pjp, this.ann("closed")));
    verify(this.locks).unlock("lock:sched:x", "o");
  }

  @Test
  void skipsWhenLockHeldElsewhere() throws Throwable {
    when(this.locks.tryLock(any(), any(), any())).thenReturn(false);
    assertNull(this.aspect.run(this.pjp, this.ann("closed")));
    verify(this.pjp, never()).proceed();
    verify(this.locks, never()).unlock(any(), any());
  }

  @Test
  void skipsWhenBackendDownAndFailClosed() throws Throwable {
    when(this.locks.tryLock(any(), any(), any())).thenThrow(new IllegalStateException("down"));
    assertNull(this.aspect.run(this.pjp, this.ann("closed")));
    verify(this.pjp, never()).proceed();
  }

  @Test
  void runsWhenBackendDownAndFailOpen() throws Throwable {
    when(this.locks.tryLock(any(), any(), any())).thenThrow(new IllegalStateException("down"));
    assertEquals("ran", this.aspect.run(this.pjp, this.ann("open")));
  }
}
