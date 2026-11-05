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

import dev.gitnode.os.actions.services.WorkflowParserService;
import dev.gitnode.os.actions.services.WorkflowTriggerService;
import dev.gitnode.os.events.pr.PullRequestCreatedEvent;
import dev.gitnode.os.events.pr.PullRequestReopenedEvent;
import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
  private final WorkflowParserService parserService;

  @ApplicationModuleListener
  void onPullRequestCreated(final PullRequestCreatedEvent event) {

    log.debug("Handling PR created for repoId={} branch={}", event.repoId(), event.sourceBranch());

    this.workflowTriggerService.triggerOnPullRequest(
        event.repoId(),
        event.sourceBranch(),
        event.targetBranch(),
        event.sourceSha(),
        event.authorId());
  }

  @ApplicationModuleListener
  void onPullRequestReopened(final PullRequestReopenedEvent event) {

    log.debug("Handling PR reopened for repoId={} branch={}", event.repoId(), event.sourceBranch());

    this.workflowTriggerService.triggerOnPullRequest(
        event.repoId(),
        event.sourceBranch(),
        event.targetBranch(),
        event.sourceSha(),
        event.actorId(),
        "reopened");
  }

  /** A merged or closed pull request reports "closed"; other status changes are not triggers. */
  @ApplicationModuleListener
  void onPullRequestClosed(final PullRequestStatusChangedEvent event) {

    final boolean merged = "MERGED".equals(event.newStatus());
    if (!merged && !"CLOSED".equals(event.newStatus())) {
      return;
    }
    // A merged pull request runs at the target branch (it holds the merge); a closed one at its
    // head, falling back to the target when the source branch is already gone.
    final var first = merged ? event.targetBranch() : event.sourceBranch();
    final var second = merged ? event.sourceBranch() : event.targetBranch();
    final var sha =
        this.resolve(event, first) != null
            ? this.resolve(event, first)
            : this.resolve(event, second);
    if (sha == null) {
      log.debug("PR {} closed, but no branch of it exists any more", event.prNumber());
      return;
    }

    this.workflowTriggerService.triggerOnPullRequest(
        event.repoId(), event.sourceBranch(), event.targetBranch(), sha, event.actorId(), "closed");
  }

  private @Nullable String resolve(final PullRequestStatusChangedEvent event, final String branch) {
    return this.parserService.resolveSha(
        event.ownerUsername(), event.repoName(), "refs/heads/" + branch);
  }
}
