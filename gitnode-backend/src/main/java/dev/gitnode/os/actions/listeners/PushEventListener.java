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
import dev.gitnode.os.branch.api.OpenPullRequestQueryPort;
import dev.gitnode.os.events.repo.RepoPushedEvent;
import dev.gitnode.os.events.repo.RepoTagPushedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import java.util.UUID;
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
public class PushEventListener {

  private final WorkflowTriggerService workflowTriggerService;
  private final OpenPullRequestQueryPort pullRequests;
  private final TenantQueryPort tenants;

  @ApplicationModuleListener
  void onRepoTagPushed(final RepoTagPushedEvent event) {

    log.debug("Handling tag push for repoId={} tag={}", event.repoId(), event.tagName());

    this.workflowTriggerService.triggerOnTag(
        event.repoId(),
        event.tagName(),
        event.pusherUsername(),
        this.actorId(event.pusherUsername()),
        event.commitSha());
  }

  @ApplicationModuleListener
  void onRepoPushed(final RepoPushedEvent event) {

    log.debug("Handling push for repoId={} branch={}", event.repoId(), event.branchName());

    final var actorId = this.actorId(event.pusherUsername());
    this.workflowTriggerService.triggerOnPush(
        event.repoId(), event.branchName(), event.pusherUsername(), actorId, event.commitSha());
    if (event.commitSha() != null) {
      for (final var target :
          this.pullRequests.findTargetBranches(event.repoId(), event.branchName())) {
        this.workflowTriggerService.triggerOnPullRequest(
            event.repoId(), event.branchName(), target, event.commitSha(), actorId);
      }
    }
  }

  private @Nullable UUID actorId(final @Nullable String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    return this.tenants.findByUsernameIgnoreCase(username).map(TenantRef::id).orElse(null);
  }
}
