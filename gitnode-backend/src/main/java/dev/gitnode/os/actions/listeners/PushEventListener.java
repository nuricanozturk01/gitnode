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
package dev.gitnode.os.actions.listeners;

import dev.gitnode.os.actions.services.WorkflowTriggerService;
import dev.gitnode.os.events.repo.RepoPushedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
@Transactional(readOnly = true)
public class PushEventListener {

  private final WorkflowTriggerService workflowTriggerService;

  @ApplicationModuleListener
  void onRepoPushed(final RepoPushedEvent event) {

    log.debug("Handling push for repoId={} branch={}", event.repoId(), event.branchName());

    this.workflowTriggerService.triggerOnPush(
        event.repoId(), event.branchName(), event.pusherUsername(), null);
  }
}
