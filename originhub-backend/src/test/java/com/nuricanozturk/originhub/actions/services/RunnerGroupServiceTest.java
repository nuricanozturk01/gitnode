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
package com.nuricanozturk.originhub.actions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.dtos.request.RunnerGroupRequest;
import com.nuricanozturk.originhub.actions.entities.RunnerGroup;
import com.nuricanozturk.originhub.actions.repositories.RunnerGroupRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("RunnerGroupService unit tests")
class RunnerGroupServiceTest {

  @Mock private RunnerGroupRepository groupRepository;

  @InjectMocks private RunnerGroupService service;

  private static final Long ORG_ID = 1L;
  private static final Long GROUP_ID = 10L;

  @Test
  @DisplayName("create saves and returns response when name is unique")
  void create_savesAndReturns() {
    final var req = new RunnerGroupRequest("builders", List.of("linux", "x64"));

    final var saved = new RunnerGroup();
    saved.setId(GROUP_ID);
    saved.setOrgId(ORG_ID);
    saved.setName("builders");
    saved.setLabels(List.of("linux", "x64"));
    saved.setCreatedAt(Instant.now());

    when(groupRepository.existsByOrgIdAndName(ORG_ID, "builders")).thenReturn(false);
    when(groupRepository.save(any(RunnerGroup.class))).thenReturn(saved);

    final var result = service.create(ORG_ID, req);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(GROUP_ID);
    assertThat(result.orgId()).isEqualTo(ORG_ID);
    assertThat(result.name()).isEqualTo("builders");
    assertThat(result.labels()).containsExactly("linux", "x64");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException when group name already exists for org")
  void create_throwsWhenDuplicate() {
    final var req = new RunnerGroupRequest("builders", null);

    when(groupRepository.existsByOrgIdAndName(ORG_ID, "builders")).thenReturn(true);

    assertThatThrownBy(() -> service.create(ORG_ID, req))
        .isInstanceOf(ErrorOccurredException.class);

    verify(groupRepository, never()).save(any());
  }

  @Test
  @DisplayName("listByOrg returns mapped responses for all groups in org")
  void listByOrg_returnsMappedResponses() {
    final var g1 = new RunnerGroup();
    g1.setId(1L);
    g1.setOrgId(ORG_ID);
    g1.setName("group-a");
    g1.setLabels(List.of("linux"));
    g1.setCreatedAt(Instant.now());

    final var g2 = new RunnerGroup();
    g2.setId(2L);
    g2.setOrgId(ORG_ID);
    g2.setName("group-b");
    g2.setLabels(List.of("windows"));
    g2.setCreatedAt(Instant.now());

    when(groupRepository.findAllByOrgId(ORG_ID, Pageable.unpaged()))
        .thenReturn(new PageImpl<>(List.of(g1, g2)));

    final var result = service.listByOrg(ORG_ID, Pageable.unpaged());

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent()).extracting("name").containsExactly("group-a", "group-b");
  }

  @Test
  @DisplayName("listByOrg returns empty list when org has no groups")
  void listByOrg_emptyOrg_returnsEmptyList() {
    when(groupRepository.findAllByOrgId(ORG_ID, Pageable.unpaged()))
        .thenReturn(new PageImpl<>(List.of()));

    final var result = service.listByOrg(ORG_ID, Pageable.unpaged());

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("delete calls repository delete when group belongs to org")
  void delete_removesGroup() {
    final var group = new RunnerGroup();
    group.setId(GROUP_ID);
    group.setOrgId(ORG_ID);
    group.setName("builders");
    group.setLabels(List.of());
    group.setCreatedAt(Instant.now());

    when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

    service.delete(GROUP_ID, ORG_ID);

    verify(groupRepository).delete(group);
  }

  @Test
  @DisplayName("delete throws ItemNotFoundException when group does not exist")
  void delete_throwsWhenGroupNotFound() {
    when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(GROUP_ID, ORG_ID))
        .isInstanceOf(ItemNotFoundException.class);

    verify(groupRepository, never()).delete(any());
  }
}
