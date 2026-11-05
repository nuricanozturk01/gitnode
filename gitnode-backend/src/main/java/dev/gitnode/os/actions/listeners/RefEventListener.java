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
import dev.gitnode.os.events.branch.BranchCreatedEvent;
import dev.gitnode.os.events.branch.BranchDeletedEvent;
import dev.gitnode.os.events.repo.RepoRefCreatedEvent;
import dev.gitnode.os.events.repo.RepoRefDeletedEvent;
import dev.gitnode.os.events.tag.ReleaseChangedEvent;
import dev.gitnode.os.events.tag.TagCreatedEvent;
import dev.gitnode.os.events.tag.TagDeletedEvent;
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

/** Starts the workflows listening for {@code create}, {@code delete} and {@code release}. */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
@Transactional(readOnly = true)
public class RefEventListener {

  private final WorkflowTriggerService workflowTriggerService;
  private final TenantQueryPort tenants;

  @ApplicationModuleListener
  void onRefCreated(final RepoRefCreatedEvent event) {

    log.debug(
        "Handling created {} {} for repoId={}", event.refType(), event.refName(), event.repoId());
    this.workflowTriggerService.triggerOnRefCreated(
        event.repoId(),
        event.refType(),
        event.refName(),
        event.sha(),
        this.actorId(event.actorUsername()));
  }

  @ApplicationModuleListener
  void onRefDeleted(final RepoRefDeletedEvent event) {

    log.debug(
        "Handling deleted {} {} for repoId={}", event.refType(), event.refName(), event.repoId());
    this.workflowTriggerService.triggerOnRefDeleted(
        event.repoId(), event.refType(), event.refName(), this.actorId(event.actorUsername()));
  }

  /** Branches made or removed through the API and the web UI (git pushes use the repo events). */
  @ApplicationModuleListener
  void onBranchCreated(final BranchCreatedEvent event) {
    this.workflowTriggerService.triggerOnRefCreated(
        event.repoId(), "branch", event.branchName(), null, null);
  }

  @ApplicationModuleListener
  void onBranchDeleted(final BranchDeletedEvent event) {
    this.workflowTriggerService.triggerOnRefDeleted(
        event.repoId(), "branch", event.branchName(), null);
  }

  @ApplicationModuleListener
  void onTagCreated(final TagCreatedEvent event) {
    this.workflowTriggerService.triggerOnRefCreated(
        event.repoId(), "tag", event.tagName(), null, null);
  }

  @ApplicationModuleListener
  void onTagDeleted(final TagDeletedEvent event) {
    this.workflowTriggerService.triggerOnRefDeleted(event.repoId(), "tag", event.tagName(), null);
  }

  @ApplicationModuleListener
  void onReleaseChanged(final ReleaseChangedEvent event) {

    log.debug(
        "Handling release {} {} for repoId={}", event.tagName(), event.actions(), event.repoId());
    this.workflowTriggerService.triggerOnRelease(
        event.repoId(),
        event.tagName(),
        event.name(),
        event.prerelease(),
        event.draft(),
        event.actions(),
        event.actorId());
  }

  private @Nullable UUID actorId(final @Nullable String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    return this.tenants.findByUsernameIgnoreCase(username).map(TenantRef::id).orElse(null);
  }
}
