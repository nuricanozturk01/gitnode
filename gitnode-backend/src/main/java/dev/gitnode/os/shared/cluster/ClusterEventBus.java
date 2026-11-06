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

import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * Fan-out of small notifications to every running instance (SSE streams are held by one instance,
 * but the event can originate on another). Delivery is best effort and not persistent.
 */
@NullMarked
public interface ClusterEventBus {

  /**
   * Sends a message to all instances, including this one.
   *
   * @return false when the bus is unavailable; the caller then delivers locally
   */
  boolean publish(String channel, String payload);

  /**
   * Registers a handler for channels matching a glob pattern such as {@code prefix:*}. The handler
   * receives the full channel name and the payload.
   */
  void subscribe(String channelPattern, BiConsumer<String, String> handler);
}
