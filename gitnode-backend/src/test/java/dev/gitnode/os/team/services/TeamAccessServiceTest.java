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
package dev.gitnode.os.team.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.gitnode.os.team.api.TeamRole;
import dev.gitnode.os.team.repositories.TeamMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamAccessService unit tests")
class TeamAccessServiceTest {

  @Mock private TeamMemberRepository repository;

  @InjectMocks private TeamAccessService service;

  private final UUID memberId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("returns the accepted role in that owner's team with one query")
  void returnsAcceptedRoleWithOneQuery() {
    when(repository.findAcceptedRole(memberId, ownerId))
        .thenReturn(Optional.of(TeamRole.MAINTAINER));

    assertThat(service.acceptedRoleOf(memberId, ownerId)).contains(TeamRole.MAINTAINER);

    verify(repository).findAcceptedRole(memberId, ownerId);
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("pending or missing membership yields no role")
  void pendingOrMissingMembershipYieldsNoRole() {
    when(repository.findAcceptedRole(memberId, ownerId)).thenReturn(Optional.empty());

    assertThat(service.acceptedRoleOf(memberId, ownerId)).isEmpty();
  }

  @Test
  @DisplayName("membership in one owner's team does not apply to another owner")
  void membershipIsScopedToOwner() {
    final var otherOwner = UUID.randomUUID();
    when(repository.findAcceptedRole(memberId, otherOwner)).thenReturn(Optional.empty());

    assertThat(service.acceptedRoleOf(memberId, otherOwner)).isEmpty();
    verify(repository).findAcceptedRole(memberId, otherOwner);
  }
}
