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
package dev.gitnode.os.task.listeners;

import dev.gitnode.os.events.pr.PullRequestCreatedEvent;
import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import dev.gitnode.os.task.services.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class PrStatusTaskListener {

  private final TaskService taskService;

  @ApplicationModuleListener
  public void onPullRequestCreated(final PullRequestCreatedEvent event) {
    log.debug(
        "PR created event received: prId={}, repoId={}, branch={}",
        event.prId(),
        event.repoId(),
        event.sourceBranch());

    this.taskService.linkPullRequest(event.repoId(), event.sourceBranch(), event.prId());
  }

  @ApplicationModuleListener
  public void onPullRequestStatusChanged(final PullRequestStatusChangedEvent event) {
    log.debug(
        "PR status changed event received: prId={}, repoId={}, branch={}, status={}",
        event.prId(),
        event.repoId(),
        event.sourceBranch(),
        event.newStatus());

    this.taskService.updateTaskStatusForPr(event.repoId(), event.sourceBranch(), event.newStatus());
  }
}
