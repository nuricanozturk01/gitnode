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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Synchronous in-memory bus shared by several "instances" in a test. */
public class InMemoryClusterEventBus implements ClusterEventBus {

  private record Subscription(String prefix, BiConsumer<String, String> handler) {}

  private final List<Subscription> subscriptions = new ArrayList<>();
  private boolean available = true;

  public void setAvailable(final boolean available) {
    this.available = available;
  }

  @Override
  public boolean publish(final String channel, final String payload) {
    if (!this.available) {
      return false;
    }
    for (final var sub : List.copyOf(this.subscriptions)) {
      if (channel.startsWith(sub.prefix())) {
        sub.handler().accept(channel, payload);
      }
    }
    return true;
  }

  @Override
  public void subscribe(final String channelPattern, final BiConsumer<String, String> handler) {
    this.subscriptions.add(
        new Subscription(channelPattern.substring(0, channelPattern.length() - 1), handler));
  }
}
