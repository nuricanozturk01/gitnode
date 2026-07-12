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
package dev.gitnode.os.notification.services;

import dev.gitnode.os.notification.dtos.NotificationPreferenceInfo;
import dev.gitnode.os.notification.entities.NotificationPreference;
import dev.gitnode.os.notification.entities.NotificationType;
import dev.gitnode.os.notification.repositories.NotificationPreferenceRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository repository;

  public List<NotificationPreferenceInfo> getAll(final UUID tenantId) {

    final var saved = this.repository.findAllByTenantId(tenantId);

    return Arrays.stream(NotificationType.values())
        .map(type -> this.toNotificationPreferenceDto(saved, type))
        .toList();
  }

  @Transactional
  public NotificationPreferenceInfo set(
      final UUID tenantId, final NotificationType type, final boolean enabled) {

    final var pref =
        this.repository
            .findByTenantIdAndType(tenantId, type)
            .orElseGet(() -> this.createNotificationPreference(tenantId, type));

    pref.setEnabled(enabled);
    this.repository.save(pref);

    return new NotificationPreferenceInfo(type, enabled);
  }

  public boolean isEnabled(final UUID tenantId, final NotificationType type) {

    return !this.repository.existsByTenantIdAndTypeAndEnabled(tenantId, type, false);
  }

  private NotificationPreferenceInfo toNotificationPreferenceDto(
      final List<NotificationPreference> saved, final NotificationType type) {

    final var pref = saved.stream().filter(p -> p.getType() == type).findFirst();

    return new NotificationPreferenceInfo(
        type, pref.map(NotificationPreference::isEnabled).orElse(true));
  }

  private NotificationPreference createNotificationPreference(
      final UUID tenantId, final NotificationType type) {

    final var p = new NotificationPreference();
    p.setTenantId(tenantId);
    p.setType(type);

    return p;
  }
}
