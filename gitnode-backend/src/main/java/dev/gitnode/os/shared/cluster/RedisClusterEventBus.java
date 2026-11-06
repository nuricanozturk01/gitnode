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
package dev.gitnode.os.shared.cluster;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/** {@link ClusterEventBus} on Redis pub/sub. */
@Slf4j
@Component
@NullMarked
public class RedisClusterEventBus implements ClusterEventBus {

  private final StringRedisTemplate redis;
  private final RedisMessageListenerContainer clusterEventContainer;

  RedisClusterEventBus(
      final StringRedisTemplate redis,
      @Qualifier("clusterEventContainer")
          final RedisMessageListenerContainer clusterEventContainer) {
    this.redis = redis;
    this.clusterEventContainer = clusterEventContainer;
  }

  @Override
  public boolean publish(final String channel, final String payload) {
    try {
      this.redis.convertAndSend(channel, payload);
      return true;
    } catch (final RuntimeException ex) {
      log.warn("Cluster event bus unavailable, delivering locally: {}", ex.getMessage());
      return false;
    }
  }

  @Override
  public void subscribe(final String channelPattern, final BiConsumer<String, String> handler) {
    this.clusterEventContainer.addMessageListener(
        (message, _) -> {
          final var channel = new String(message.getChannel(), StandardCharsets.UTF_8);
          final var body = new String(message.getBody(), StandardCharsets.UTF_8);
          try {
            handler.accept(channel, body);
          } catch (final RuntimeException ex) {
            log.warn("Cluster event handler failed for {}: {}", channel, ex.getMessage());
          }
        },
        new PatternTopic(channelPattern));
  }
}
