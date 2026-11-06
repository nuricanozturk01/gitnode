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
package dev.gitnode.os.ai.configs;

import java.util.concurrent.Executor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@NullMarked
public class AiAsyncConfig {

  private static final int AI_EXECUTOR_MAX_POOL_SIZE = 3;
  private static final int AI_EXECUTOR_QUEUE_CAPACITY = 16;
  private static final int AI_EXECUTOR_AWAIT_TERMINATION_SECONDS = 30;

  @Bean(name = "aiReviewExecutor")
  Executor aiReviewExecutor(final TaskDecorator decorator) {
    return buildExecutor(
        "gitnode-ai-review-", 2, AI_EXECUTOR_MAX_POOL_SIZE, AI_EXECUTOR_QUEUE_CAPACITY, decorator);
  }

  @Bean(name = "aiAnalysisExecutor")
  Executor aiAnalysisExecutor(final TaskDecorator decorator) {
    return buildExecutor(
        "gitnode-ai-analysis-",
        2,
        AI_EXECUTOR_MAX_POOL_SIZE,
        AI_EXECUTOR_QUEUE_CAPACITY,
        decorator);
  }

  private static ThreadPoolTaskExecutor buildExecutor(
      final String prefix,
      final int core,
      final int max,
      final int queue,
      final TaskDecorator decorator) {
    final var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(core);
    executor.setMaxPoolSize(max);
    executor.setQueueCapacity(queue);
    executor.setThreadNamePrefix(prefix);
    executor.setTaskDecorator(decorator);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(AI_EXECUTOR_AWAIT_TERMINATION_SECONDS);
    executor.initialize();
    return executor;
  }
}
