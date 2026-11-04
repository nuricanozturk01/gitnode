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
import dev.gitnode.os.events.actions.WorkflowRunCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Starts the workflows that listen for another workflow to finish ({@code on: workflow_run}). */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class WorkflowRunEventListener {

  private final WorkflowTriggerService workflowTriggerService;

  @ApplicationModuleListener
  void onWorkflowRunCompleted(final WorkflowRunCompletedEvent event) {
    log.debug("Handling completed run {} of {}", event.runId(), event.workflowName());
    this.workflowTriggerService.triggerOnWorkflowRunCompleted(event.repoId(), event.runId());
  }
}
