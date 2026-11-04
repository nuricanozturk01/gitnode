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
package dev.gitnode.os.repo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.repositories.RepoRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepoService GitAccessPort")
class RepoServiceGitAccessTest {

  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private RepoRepository repoRepository;
  @Mock private CollaboratorAccessPort collaboratorAccessPort;

  @InjectMocks private RepoService repoService;

  private Repo stubRepo(final boolean isPrivate) {
    final var owner = new Tenant();
    owner.setId(OWNER_ID);
    owner.setUsername("alice");
    final var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwner(owner);
    repo.setPrivate(isPrivate);
    when(repoRepository.findByOwnerUsernameAndName("alice", "repo")).thenReturn(Optional.of(repo));
    return repo;
  }

  @Test
  @DisplayName("public repo is readable anonymously but not writable by a stranger")
  void publicRepo_readableByAnyone_notWritableByStranger() {
    final var repo = stubRepo(false);
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(false);

    assertThat(repoService.canRead(null, "alice", "repo")).isTrue();
    assertThat(repoService.canRead(USER_ID, "alice", "repo")).isTrue();
    assertThat(repoService.canWrite(USER_ID, "alice", "repo")).isFalse();
  }

  @Test
  @DisplayName("private repo is not readable anonymously")
  void privateRepo_anonymous_cannotRead() {
    stubRepo(true);

    assertThat(repoService.canRead(null, "alice", "repo")).isFalse();
  }

  @Test
  @DisplayName("owner can read and write a private repo")
  void privateRepo_owner_canReadAndWrite() {
    stubRepo(true);

    assertThat(repoService.canRead(OWNER_ID, "alice", "repo")).isTrue();
    assertThat(repoService.canWrite(OWNER_ID, "alice", "repo")).isTrue();
  }

  @Test
  @DisplayName("stranger can neither read nor write a private repo")
  void privateRepo_stranger_denied() {
    final var repo = stubRepo(true);
    when(collaboratorAccessPort.isActiveCollaborator(repo.getId(), USER_ID)).thenReturn(false);
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(false);

    assertThat(repoService.canRead(USER_ID, "alice", "repo")).isFalse();
    assertThat(repoService.canWrite(USER_ID, "alice", "repo")).isFalse();
  }

  @Test
  @DisplayName("active collaborator or team member reads; PUSH grants write")
  void privateRepo_memberWithPush_canReadAndWrite() {
    final var repo = stubRepo(true);
    when(collaboratorAccessPort.isActiveCollaborator(repo.getId(), USER_ID)).thenReturn(true);
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(true);

    assertThat(repoService.canRead(USER_ID, "alice", "repo")).isTrue();
    assertThat(repoService.canWrite(USER_ID, "alice", "repo")).isTrue();
  }

  @Test
  @DisplayName("read-only member (team VIEWER or READ collaborator) cannot write")
  void privateRepo_readOnlyMember_cannotWrite() {
    final var repo = stubRepo(true);
    when(collaboratorAccessPort.isActiveCollaborator(repo.getId(), USER_ID)).thenReturn(true);
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(false);

    assertThat(repoService.canRead(USER_ID, "alice", "repo")).isTrue();
    assertThat(repoService.canWrite(USER_ID, "alice", "repo")).isFalse();
  }

  @Test
  @DisplayName("unknown repo denies read and write")
  void unknownRepo_denied() {
    when(repoRepository.findByOwnerUsernameAndName("alice", "ghost")).thenReturn(Optional.empty());

    assertThat(repoService.canRead(null, "alice", "ghost")).isFalse();
    assertThat(repoService.canRead(OWNER_ID, "alice", "ghost")).isFalse();
    assertThat(repoService.canWrite(OWNER_ID, "alice", "ghost")).isFalse();
  }
}
