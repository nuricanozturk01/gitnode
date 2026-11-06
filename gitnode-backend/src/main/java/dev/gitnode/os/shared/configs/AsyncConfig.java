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
package dev.gitnode.os.shared.configs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executors for {@code @Async} work. Every {@code @Async} names its executor so one slow kind of
 * work (webhooks, repository events, migrations) cannot starve another. All pools have bounded
 * queues. When a pool is saturated the calling thread runs the task, so nothing is lost; audit
 * entries are the exception: they are best effort and are dropped and counted instead of slowing
 * requests down.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
@NullMarked
public class AsyncConfig implements AsyncConfigurer {

  public static final String APPLICATION_EXECUTOR = "applicationAsyncExecutor";
  public static final String WEBHOOK_EXECUTOR = "webhookExecutor";
  public static final String AUDIT_EXECUTOR = "auditExecutor";
  public static final String REPO_EVENT_EXECUTOR = "repoEventExecutor";
  public static final String MIGRATION_EXECUTOR = "migrationExecutor";

  private static final int AWAIT_TERMINATION_SECONDS = 30;

  private static final int APP_CORE = 4;
  private static final int APP_MAX = 16;
  private static final int APP_QUEUE = 1000;
  private static final int WEBHOOK_CORE = 4;
  private static final int WEBHOOK_MAX = 16;
  private static final int WEBHOOK_QUEUE = 1000;
  private static final int AUDIT_CORE = 2;
  private static final int AUDIT_MAX = 4;
  private static final int AUDIT_QUEUE = 2000;
  private static final int REPO_EVENT_CORE = 2;
  private static final int REPO_EVENT_MAX = 8;
  private static final int REPO_EVENT_QUEUE = 500;
  private static final int MIGRATION_CORE = 2;
  private static final int MIGRATION_MAX = 4;
  private static final int MIGRATION_QUEUE = 50;

  private final MeterRegistry meters;
  private final TaskDecorator decorator = new MdcTaskDecorator();

  AsyncConfig(final MeterRegistry meters) {
    this.meters = meters;
  }

  /** Carries the logging context (request id, user) onto the worker thread. */
  static final class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(final Runnable task) {
      final Map<String, String> context = MDC.getCopyOfContextMap();
      if (context == null) {
        return task;
      }
      return () -> {
        final Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.setContextMap(context);
        try {
          task.run();
        } finally {
          if (previous == null) {
            MDC.clear();
          } else {
            MDC.setContextMap(previous);
          }
        }
      };
    }
  }

  @Bean(name = APPLICATION_EXECUTOR)
  Executor applicationAsyncExecutor() {
    return this.pool(
        "gitnode-async-",
        APPLICATION_EXECUTOR,
        APP_CORE,
        APP_MAX,
        APP_QUEUE,
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean(name = WEBHOOK_EXECUTOR)
  Executor webhookExecutor() {
    return this.pool(
        "gitnode-webhook-",
        WEBHOOK_EXECUTOR,
        WEBHOOK_CORE,
        WEBHOOK_MAX,
        WEBHOOK_QUEUE,
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean(name = AUDIT_EXECUTOR)
  Executor auditExecutor() {
    final Counter dropped =
        this.meters.counter("gitnode.async.dropped", "executor", AUDIT_EXECUTOR);
    return this.pool(
        "gitnode-audit-",
        AUDIT_EXECUTOR,
        AUDIT_CORE,
        AUDIT_MAX,
        AUDIT_QUEUE,
        (task, _) -> {
          dropped.increment();
          log.warn("Audit queue is full, dropping an audit entry");
        });
  }

  @Bean(name = REPO_EVENT_EXECUTOR)
  Executor repoEventExecutor() {
    return this.pool(
        "gitnode-repo-event-",
        REPO_EVENT_EXECUTOR,
        REPO_EVENT_CORE,
        REPO_EVENT_MAX,
        REPO_EVENT_QUEUE,
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean(name = MIGRATION_EXECUTOR)
  Executor migrationExecutor() {
    return this.pool(
        "gitnode-migration-",
        MIGRATION_EXECUTOR,
        MIGRATION_CORE,
        MIGRATION_MAX,
        MIGRATION_QUEUE,
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /** Decorator shared with the AI pools so they carry the logging context too. */
  @Bean
  TaskDecorator asyncTaskDecorator() {
    return this.decorator;
  }

  @Override
  public Executor getAsyncExecutor() {
    return this.applicationAsyncExecutor();
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    final Counter failures = this.meters.counter("gitnode.async.failures");
    return (throwable, method, _) -> {
      failures.increment();
      log.error(
          "Uncaught async failure in {}: {}",
          method.toGenericString(),
          throwable.getMessage(),
          throwable);
    };
  }

  private ThreadPoolTaskExecutor pool(
      final String prefix,
      final String name,
      final int core,
      final int max,
      final int queue,
      final RejectedExecutionHandler rejected) {
    final var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(core);
    executor.setMaxPoolSize(max);
    executor.setQueueCapacity(queue);
    executor.setThreadNamePrefix(prefix);
    executor.setTaskDecorator(this.decorator);
    executor.setRejectedExecutionHandler(rejected);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
    executor.initialize();
    new ExecutorServiceMetrics(executor.getThreadPoolExecutor(), name, Tags.empty())
        .bindTo(this.meters);
    return executor;
  }
}
