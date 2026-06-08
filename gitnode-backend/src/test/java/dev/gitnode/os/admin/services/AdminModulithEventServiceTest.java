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
package dev.gitnode.os.admin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.ModulithEventLifecycleFilter;
import dev.gitnode.os.admin.entities.EventPublicationRecord;
import dev.gitnode.os.admin.repositories.EventPublicationRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminModulithEventService unit tests")
class AdminModulithEventServiceTest {

  @Mock private AdminPlatformSettingsService adminPlatformSettingsService;
  @Mock private EventPublicationRepository eventPublicationRepository;

  @InjectMocks private AdminModulithEventService adminModulithEventService;

  @Test
  @DisplayName("returns empty response without querying database when disabled")
  void search_skipsDatabase_whenDisabled() {
    when(adminPlatformSettingsService.isModulithEventsViewerEnabled()).thenReturn(false);

    var response =
        adminModulithEventService.search(
            PageRequest.of(0, 20),
            null,
            null,
            null,
            null,
            ModulithEventLifecycleFilter.ALL,
            null,
            null);

    assertThat(response.available()).isFalse();
    assertThat(response.content()).isEmpty();
    verifyNoInteractions(eventPublicationRepository);
  }

  @Test
  @DisplayName("searches repository when viewer is enabled")
  void search_queriesDatabase_whenEnabled() {
    when(adminPlatformSettingsService.isModulithEventsViewerEnabled()).thenReturn(true);
    var record = sampleRecord();
    when(eventPublicationRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(record)));

    var response =
        adminModulithEventService.search(
            PageRequest.of(0, 20),
            "repo",
            "IssueCreatedEvent",
            "listener",
            "COMPLETED",
            ModulithEventLifecycleFilter.COMPLETED,
            Instant.EPOCH,
            Instant.EPOCH);

    assertThat(response.available()).isTrue();
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).eventType()).isEqualTo("IssueCreatedEvent");
    verify(eventPublicationRepository).findAll(any(Specification.class), any(PageRequest.class));
  }

  @Test
  @DisplayName("getDetail returns mapped record when enabled")
  void getDetail_returnsDetail_whenEnabled() {
    when(adminPlatformSettingsService.isModulithEventsViewerEnabled()).thenReturn(true);
    var record = sampleRecord();
    when(eventPublicationRepository.findById(record.getId())).thenReturn(Optional.of(record));

    var detail = adminModulithEventService.getDetail(record.getId());

    assertThat(detail.eventType()).isEqualTo("IssueCreatedEvent");
    assertThat(detail.serializedEvent()).isEqualTo("{\"id\":1}");
  }

  @Test
  @DisplayName("getDetail throws when viewer is disabled")
  void getDetail_throws_whenDisabled() {
    when(adminPlatformSettingsService.isModulithEventsViewerEnabled()).thenReturn(false);

    assertThatThrownBy(() -> adminModulithEventService.getDetail(UUID.randomUUID()))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  @DisplayName("listFilterOptions returns empty lists when disabled")
  void listFilterOptions_returnsEmpty_whenDisabled() {
    when(adminPlatformSettingsService.isModulithEventsViewerEnabled()).thenReturn(false);

    var filters = adminModulithEventService.listFilterOptions();

    assertThat(filters.eventTypes()).isEmpty();
    assertThat(filters.truncated()).isFalse();
    verifyNoInteractions(eventPublicationRepository);
  }

  @Test
  @DisplayName("listFilterOptions loads distinct values when enabled")
  void listFilterOptions_loadsDistinctValues_whenEnabled() {
    when(adminPlatformSettingsService.isModulithEventsViewerEnabled()).thenReturn(true);
    when(eventPublicationRepository.findDistinctEventTypes(any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of("IssueCreatedEvent")));
    when(eventPublicationRepository.findDistinctListenerIds(any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of("issue-listener")));
    when(eventPublicationRepository.findDistinctStatuses(any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of("COMPLETED")));

    var filters = adminModulithEventService.listFilterOptions();

    assertThat(filters.eventTypes()).containsExactly("IssueCreatedEvent");
    assertThat(filters.listenerIds()).containsExactly("issue-listener");
    assertThat(filters.statuses()).containsExactly("COMPLETED");
    assertThat(filters.truncated()).isFalse();
  }

  @Test
  @DisplayName("capPageSize clamps invalid and oversized values")
  void capPageSize_clampsValues() {
    assertThat(AdminModulithEventService.capPageSize(0)).isEqualTo(20);
    assertThat(AdminModulithEventService.capPageSize(-5)).isEqualTo(20);
    assertThat(AdminModulithEventService.capPageSize(25)).isEqualTo(25);
    assertThat(AdminModulithEventService.capPageSize(500)).isEqualTo(50);
  }

  private static EventPublicationRecord sampleRecord() {
    var record = new EventPublicationRecord();
    org.springframework.test.util.ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    org.springframework.test.util.ReflectionTestUtils.setField(record, "listenerId", "listener");
    org.springframework.test.util.ReflectionTestUtils.setField(
        record, "eventType", "IssueCreatedEvent");
    org.springframework.test.util.ReflectionTestUtils.setField(
        record, "serializedEvent", "{\"id\":1}");
    org.springframework.test.util.ReflectionTestUtils.setField(
        record, "publicationDate", Instant.EPOCH);
    org.springframework.test.util.ReflectionTestUtils.setField(record, "status", "COMPLETED");
    org.springframework.test.util.ReflectionTestUtils.setField(record, "completionAttempts", 1);
    return record;
  }
}
