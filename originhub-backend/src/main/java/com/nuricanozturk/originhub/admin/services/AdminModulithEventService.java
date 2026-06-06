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
package com.nuricanozturk.originhub.admin.services;

import com.nuricanozturk.originhub.admin.dtos.ModulithEventDetail;
import com.nuricanozturk.originhub.admin.dtos.ModulithEventFilters;
import com.nuricanozturk.originhub.admin.dtos.ModulithEventLifecycleFilter;
import com.nuricanozturk.originhub.admin.dtos.ModulithEventSearchResponse;
import com.nuricanozturk.originhub.admin.dtos.ModulithEventSummary;
import com.nuricanozturk.originhub.admin.entities.EventPublicationRecord;
import com.nuricanozturk.originhub.admin.repositories.EventPublicationRepository;
import com.nuricanozturk.originhub.admin.specifications.EventPublicationSpecifications;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminModulithEventService {

  static final int MAX_PAGE_SIZE = 50;
  static final int FILTER_OPTIONS_LIMIT = 200;
  static final int EVENT_PREVIEW_MAX_LENGTH = 240;
  static final int DEFAULT_PAGE_SIZE = 20;
  public static final String DISABLED_MESSAGE =
      "Modulith event viewer is disabled in platform settings to avoid memory and database load.";

  private final AdminPlatformSettingsService adminPlatformSettingsService;
  private final EventPublicationRepository eventPublicationRepository;

  public boolean isAvailable() {

    return this.adminPlatformSettingsService.isModulithEventsViewerEnabled();
  }

  @Transactional(readOnly = true)
  public ModulithEventSearchResponse search(
      final Pageable pageable,
      final @Nullable String q,
      final @Nullable String eventType,
      final @Nullable String listenerId,
      final @Nullable String status,
      final ModulithEventLifecycleFilter lifecycle,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    if (!this.isAvailable()) {
      return disabledResponse(pageable);
    }

    final Page<EventPublicationRecord> page =
        this.eventPublicationRepository.findAll(
            EventPublicationSpecifications.search(
                blankToNull(q),
                blankToNull(eventType),
                blankToNull(listenerId),
                blankToNull(status),
                lifecycle,
                from,
                to),
            pageable);

    return new ModulithEventSearchResponse(
        page.getContent().stream().map(AdminModulithEventService::toSummary).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        true,
        "Spring Modulith events persisted in event_publication.");
  }

  @Transactional(readOnly = true)
  public ModulithEventDetail getDetail(final UUID id) {

    if (!this.isAvailable()) {
      throw new ItemNotFoundException("Modulith event viewer is disabled");
    }

    final EventPublicationRecord record =
        this.eventPublicationRepository
            .findById(id)
            .orElseThrow(() -> new ItemNotFoundException("Modulith event not found"));

    return toDetail(record);
  }

  @Transactional(readOnly = true)
  public ModulithEventFilters listFilterOptions() {

    if (!this.isAvailable()) {
      return new ModulithEventFilters(List.of(), List.of(), List.of(), false);
    }

    final var pageable = PageRequest.of(0, FILTER_OPTIONS_LIMIT);
    final var eventTypes = this.eventPublicationRepository.findDistinctEventTypes(pageable);
    final var listenerIds = this.eventPublicationRepository.findDistinctListenerIds(pageable);
    final var statuses = this.eventPublicationRepository.findDistinctStatuses(pageable);

    final boolean truncated =
        eventTypes.getTotalElements() > FILTER_OPTIONS_LIMIT
            || listenerIds.getTotalElements() > FILTER_OPTIONS_LIMIT
            || statuses.getTotalElements() > FILTER_OPTIONS_LIMIT;
    return new ModulithEventFilters(
        eventTypes.getContent(), listenerIds.getContent(), statuses.getContent(), truncated);
  }

  public static int capPageSize(final int size) {

    return Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
  }

  private static ModulithEventSearchResponse disabledResponse(final Pageable pageable) {

    return new ModulithEventSearchResponse(
        List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false, DISABLED_MESSAGE);
  }

  private static ModulithEventSummary toSummary(final EventPublicationRecord record) {

    return new ModulithEventSummary(
        record.getId(),
        record.getListenerId(),
        record.getEventType(),
        record.getPublicationDate(),
        record.getCompletionDate(),
        record.getStatus(),
        record.getCompletionAttempts(),
        record.getLastResubmissionDate(),
        preview(record.getSerializedEvent()));
  }

  private static ModulithEventDetail toDetail(final EventPublicationRecord record) {

    return new ModulithEventDetail(
        record.getId(),
        record.getListenerId(),
        record.getEventType(),
        record.getSerializedEvent(),
        record.getPublicationDate(),
        record.getCompletionDate(),
        record.getStatus(),
        record.getCompletionAttempts(),
        record.getLastResubmissionDate());
  }

  private static String preview(final String serializedEvent) {

    if (serializedEvent.length() <= EVENT_PREVIEW_MAX_LENGTH) {
      return serializedEvent;
    }

    return serializedEvent.substring(0, EVENT_PREVIEW_MAX_LENGTH) + "…";
  }

  private static @Nullable String blankToNull(final @Nullable String value) {

    if (value == null || value.isBlank()) {
      return null;
    }

    return value.trim();
  }
}
