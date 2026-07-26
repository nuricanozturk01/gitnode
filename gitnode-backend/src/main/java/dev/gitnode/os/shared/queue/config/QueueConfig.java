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
package dev.gitnode.os.shared.queue.config;

import static dev.gitnode.os.shared.queue.utils.RabbitQueueUtils.REPSY_ROUTING_KEY;
import static dev.gitnode.os.shared.queue.utils.RabbitQueueUtils.USER_MANAGEMENT_EVENT;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration
@RequiredArgsConstructor
public class QueueConfig {

  @Bean
  public DirectExchange provideUserManagementExchange() {
    return new DirectExchange(USER_MANAGEMENT_EVENT);
  }

  @Bean
  public Queue provideUserManagementQueue() {
    return new Queue(USER_MANAGEMENT_EVENT, true);
  }

  @Bean
  public Binding provideUserManagementBinding(final Queue queue, final DirectExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).with(REPSY_ROUTING_KEY);
  }

  @Bean
  public JacksonJsonMessageConverter provideJackson2JsonMessageConverter() {
    return new JacksonJsonMessageConverter("*");
  }

  @Bean
  public RabbitTemplate provideRabbitTemplate(
      final ConnectionFactory connectionFactory, final JacksonJsonMessageConverter converter) {
    final var template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    return template;
  }
}
