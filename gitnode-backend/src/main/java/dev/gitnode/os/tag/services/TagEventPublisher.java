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
package dev.gitnode.os.tag.services;

import dev.gitnode.os.events.tag.TagCreatedEvent;
import dev.gitnode.os.events.tag.TagDeletedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes tag events inside a transaction. Tag operations only touch git, so there is no
 * surrounding transaction, and listeners declared with {@code @ApplicationModuleListener} only
 * receive events published in one (they run after commit and are recorded for retry). Inside an
 * existing transaction the call simply joins it.
 */
@Component
@RequiredArgsConstructor
@NullMarked
public class TagEventPublisher {

  private final ApplicationEventPublisher events;

  @Transactional
  public void created(final UUID repoId, final String tagName) {
    this.events.publishEvent(new TagCreatedEvent(repoId, tagName));
  }

  @Transactional
  public void deleted(final UUID repoId, final String tagName) {
    this.events.publishEvent(new TagDeletedEvent(repoId, tagName));
  }
}
