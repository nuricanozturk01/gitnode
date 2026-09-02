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
package dev.gitnode.os.shared.queue.services;

import dev.gitnode.os.events.queue.QueueBaseMessage;
import dev.gitnode.os.shared.queue.utils.RabbitQueueUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@NullMarked
@RequiredArgsConstructor
public class QueueMessageTemplate {

  private final RabbitTemplate rabbitTemplate;

  @Async
  public void sendAsync(final QueueBaseMessage message) {

    this.rabbitTemplate.convertAndSend(
        RabbitQueueUtils.USER_MANAGEMENT_EVENT, RabbitQueueUtils.REPSY_ROUTING_KEY, message);
  }
}
