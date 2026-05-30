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
package com.nuricanozturk.originhub.task.listeners;

import com.nuricanozturk.originhub.shared.pr.events.PullRequestCreatedEvent;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.task.services.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrStatusTaskListener {

  private final @NonNull TaskService taskService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPullRequestCreated(final @NonNull PullRequestCreatedEvent event) {
    log.debug(
        "PR created event received: prId={}, repoId={}, branch={}",
        event.prId(),
        event.repoId(),
        event.sourceBranch());

    this.taskService.linkPullRequest(event.repoId(), event.sourceBranch(), event.prId());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPullRequestStatusChanged(final @NonNull PullRequestStatusChangedEvent event) {
    log.debug(
        "PR status changed event received: prId={}, repoId={}, branch={}, status={}",
        event.prId(),
        event.repoId(),
        event.sourceBranch(),
        event.newStatus());

    this.taskService.updateTaskStatusForPr(event.repoId(), event.sourceBranch(), event.newStatus());
  }
}
