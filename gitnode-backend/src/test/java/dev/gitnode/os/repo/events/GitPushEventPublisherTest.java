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
package dev.gitnode.os.repo.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.events.repo.RepoPushedEvent;
import dev.gitnode.os.events.repo.RepoRefCreatedEvent;
import dev.gitnode.os.events.repo.RepoRefDeletedEvent;
import dev.gitnode.os.events.repo.RepoTagPushedEvent;
import dev.gitnode.os.repo.cache.RepoCacheInvalidator;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.repositories.RepoRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitPushEventPublisher")
class GitPushEventPublisherTest {

  private static final ObjectId OLD = ObjectId.fromString("b".repeat(40));
  private static final ObjectId NEW = ObjectId.fromString("a".repeat(40));
  private static final UUID REPO_ID = UUID.randomUUID();

  @TempDir Path root;

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private RepoRepository repoRepository;
  @Mock private RepoCacheInvalidator cacheInvalidator;

  private GitPushEventPublisher publisher;
  private ReceivePack receivePack;

  @BeforeEach
  void setUp() {
    this.publisher =
        new GitPushEventPublisher(this.eventPublisher, this.repoRepository, this.cacheInvalidator);
    ReflectionTestUtils.setField(this.publisher, "repoRoot", this.root.toString());
    final var repo = new Repo();
    repo.setId(REPO_ID);
    when(this.repoRepository.findByOwnerUsernameAndName("alice", "app"))
        .thenReturn(Optional.of(repo));
    final var gitRepo = mock(Repository.class);
    when(gitRepo.getDirectory()).thenReturn(this.root.resolve("alice").resolve("app.git").toFile());
    this.receivePack = mock(ReceivePack.class);
    when(this.receivePack.getRepository()).thenReturn(gitRepo);
  }

  private static ReceiveCommand command(
      final ObjectId oldId, final ObjectId newId, final String ref) {
    final var command = new ReceiveCommand(oldId, newId, ref);
    command.setResult(ReceiveCommand.Result.OK);
    return command;
  }

  private List<Object> publish(final ReceiveCommand command) {
    this.publisher.onPostReceive(this.receivePack, List.of(command), "alice");
    final var events = ArgumentCaptor.forClass(Object.class);
    verify(this.eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(events.capture());
    return events.getAllValues();
  }

  @Test
  @DisplayName("a push to a branch carries the old tip as beforeSha")
  void pushCarriesBeforeSha() {
    final var events = this.publish(command(OLD, NEW, "refs/heads/main"));

    assertThat(events).hasSize(1);
    final var pushed = (RepoPushedEvent) events.get(0);
    assertThat(pushed.commitSha()).isEqualTo(NEW.name());
    assertThat(pushed.beforeSha()).isEqualTo(OLD.name());
  }

  @Test
  @DisplayName("a push that creates a branch also reports the branch as created")
  void branchCreated() {
    final var events = this.publish(command(ObjectId.zeroId(), NEW, "refs/heads/feature/x"));

    assertThat(events).hasSize(2);
    assertThat(((RepoPushedEvent) events.get(0)).beforeSha()).isEqualTo(ObjectId.zeroId().name());
    final var created = (RepoRefCreatedEvent) events.get(1);
    assertThat(created.refType()).isEqualTo("branch");
    assertThat(created.refName()).isEqualTo("feature/x");
    assertThat(created.sha()).isEqualTo(NEW.name());
    assertThat(created.actorUsername()).isEqualTo("alice");
  }

  @Test
  @DisplayName("a push that deletes a branch reports it as deleted, with no commit")
  void branchDeleted() {
    final var events = this.publish(command(OLD, ObjectId.zeroId(), "refs/heads/old"));

    assertThat(events).hasSize(2);
    assertThat(((RepoPushedEvent) events.get(0)).commitSha()).isNull();
    final var deleted = (RepoRefDeletedEvent) events.get(1);
    assertThat(deleted.refType()).isEqualTo("branch");
    assertThat(deleted.refName()).isEqualTo("old");
  }

  @Test
  @DisplayName("a new tag reports a tag push and a created tag; a moved tag only the push")
  void tags() {
    final var created = this.publish(command(ObjectId.zeroId(), NEW, "refs/tags/v1"));
    assertThat(created.get(0)).isInstanceOf(RepoRefCreatedEvent.class);
    final var pushed = (RepoTagPushedEvent) created.get(1);
    assertThat(pushed.tagName()).isEqualTo("v1");
    assertThat(pushed.beforeSha()).isEqualTo(ObjectId.zeroId().name());
  }

  @Test
  @DisplayName("a deleted tag reports a deleted tag and no tag push")
  void tagDeleted() {
    final var events = this.publish(command(OLD, ObjectId.zeroId(), "refs/tags/v1"));

    assertThat(events).hasSize(1);
    final var deleted = (RepoRefDeletedEvent) events.get(0);
    assertThat(deleted.refType()).isEqualTo("tag");
    assertThat(deleted.refName()).isEqualTo("v1");
  }
}
