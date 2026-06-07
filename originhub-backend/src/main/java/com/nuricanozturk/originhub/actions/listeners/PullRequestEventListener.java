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
package com.nuricanozturk.originhub.actions.listeners;

import com.nuricanozturk.originhub.actions.services.WorkflowTriggerService;
import com.nuricanozturk.originhub.events.pr.PullRequestCreatedEvent;
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
public class PullRequestEventListener {

  private final WorkflowTriggerService workflowTriggerService;

  @ApplicationModuleListener
  void onPullRequestCreated(final PullRequestCreatedEvent event) {

    log.debug("Handling PR created for repoId={} branch={}", event.repoId(), event.sourceBranch());

    this.workflowTriggerService.triggerOnPullRequest(event.repoId(), event.sourceBranch(), null);
  }
}
